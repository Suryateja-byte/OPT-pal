import * as admin from "firebase-admin";
import { createHash } from "crypto";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { SchemaType, VertexAI } from "@google-cloud/vertexai";
import { firestore } from "./documentSecurity";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const uscisClientId = defineSecret("USCIS_TORCH_CLIENT_ID");
const uscisClientSecret = defineSecret("USCIS_TORCH_CLIENT_SECRET");

const vertexAI = new VertexAI({
  project: process.env.GCLOUD_PROJECT || "opt-pal",
  location: "us-central1",
});
const model = vertexAI.getGenerativeModel({model: "gemini-2.5-flash"});

const TRACKER_SOURCE_URL = "https://egov.uscis.gov/";
const TRACKER_SOURCE_LABEL = "USCIS Case Status Online";
const MANUAL_REFRESH_COOLDOWN_MS = 15 * 60 * 1000;
const DEFAULT_POLL_INTERVAL_MS = 12 * 60 * 60 * 1000;
const FAILURE_BACKOFF_INTERVAL_MS = 24 * 60 * 60 * 1000;
const FAILURE_BACKOFF_THRESHOLD = 3;
const MAX_TRACKED_CASES = 3;
const MAX_POLLED_CASES_PER_RUN = 200;
const USCIS_NOTIFICATION_TYPE = "uscis_case_update";
const DEVICE_PLATFORM = "android";

let cachedAccessToken: {
  mode: TrackerMode;
  accessToken: string;
  expiresAt: number;
} | null = null;

type TrackerMode = "disabled" | "sandbox" | "production";
type CaseStage =
  | "RECEIVED"
  | "ACTIVE_REVIEW"
  | "BIOMETRICS"
  | "RFE_OR_NOID"
  | "CORRESPONDENCE_RECEIVED"
  | "TRANSFERRED"
  | "APPROVED"
  | "CARD_PRODUCED"
  | "CARD_PICKED_UP"
  | "CARD_DELIVERED"
  | "DENIED"
  | "REJECTED"
  | "WITHDRAWN"
  | "UNKNOWN";
type InterpretationClassification = "informational" | "consult_dso_attorney";
type InterpretationConfidence = "low" | "medium" | "high";

type UscisCaseHistoryEntry = {
  statusText: string;
  statusDescription: string;
  statusDate: number | null;
};

type ParsedUscisCaseResponse = {
  receiptNumber: string;
  formType: string;
  officialStatusText: string;
  officialStatusDescription: string;
  modifiedAt: number | null;
  history: UscisCaseHistoryEntry[];
};

type UscisCaseTrackerRecord = {
  receiptNumber: string;
  formType: string;
  normalizedStage: CaseStage;
  officialStatusText: string;
  officialStatusDescription: string;
  officialHistory: UscisCaseHistoryEntry[];
  plainEnglishSummary: string;
  recommendedAction: string;
  classification: InterpretationClassification;
  confidence: InterpretationConfidence;
  watchFor: string[];
  statusHash: string;
  lastCheckedAt: number;
  lastChangedAt: number;
  nextPollAt: number;
  lastError: string;
  consecutiveFailureCount: number;
  isTerminal: boolean;
  isArchived: boolean;
};

type NotificationDeviceRecord = {
  installationId: string;
  token: string;
  platform: string;
  enabled: boolean;
  caseStatusEnabled: boolean;
  policyAlertsEnabled: boolean;
  createdAt: number;
  updatedAt: number;
};

type AvailabilityResponse = {
  mode: TrackerMode;
  reason: string;
  maxTrackedCases: number;
};

type InterpretationResult = {
  plainEnglishSummary: string;
  confidence: InterpretationConfidence;
  classification: InterpretationClassification;
  watchFor: string[];
};

type RefreshResult = {
  refreshed: boolean;
  statusChanged: boolean;
  cooldownRemainingMinutes: number;
  caseId: string;
  tracker: UscisCaseTrackerRecord;
};

export const getUscisTrackerAvailability = onCall(
  {enforceAppCheck: true},
  async (request): Promise<AvailabilityResponse> => {
    requireAuth(request.auth);
    return buildTrackerAvailability();
  }
);

export const trackUscisCase = onCall(
  {enforceAppCheck: true, secrets: [uscisClientId, uscisClientSecret]},
  async (request) => {
    const userId = requireAuth(request.auth);
    ensureTrackerEnabled();
    const receiptNumber = normalizeReceiptNumber(request.data.receiptNumber);
    const caseRef = trackerDocRef(userId, receiptNumber);
    const existingSnapshot = await caseRef.get();
    const existingRecord = toTrackerRecord(existingSnapshot.data());

    const activeCountSnapshot = await trackerCollection(userId)
      .where("isArchived", "==", false)
      .get();
    const otherActiveCount = activeCountSnapshot.docs.filter((doc) => doc.id !== receiptNumber).length;
    if (!existingRecord?.isArchived && existingSnapshot.exists) {
      return {
        caseId: receiptNumber,
        alreadyTracked: true,
      };
    }
    if (otherActiveCount >= MAX_TRACKED_CASES) {
      throw new HttpsError(
        "resource-exhausted",
        `You can track up to ${MAX_TRACKED_CASES} active I-765 cases in this version.`
      );
    }

    const refreshResult = await refreshTrackerRecord({
      userId,
      receiptNumber,
      existingRecord,
      bypassCooldown: true,
      shouldNotifyOnChange: false,
    });

    return {
      caseId: receiptNumber,
      alreadyTracked: false,
      statusChanged: refreshResult.statusChanged,
    };
  }
);

export const refreshUscisCase = onCall(
  {enforceAppCheck: true, secrets: [uscisClientId, uscisClientSecret]},
  async (request): Promise<RefreshResult> => {
    const userId = requireAuth(request.auth);
    ensureTrackerEnabled();
    const caseId = normalizeReceiptNumber(request.data.caseId);
    const snapshot = await trackerDocRef(userId, caseId).get();
    if (!snapshot.exists) {
      throw new HttpsError("not-found", "Tracked case not found.");
    }
    const existingRecord = toTrackerRecord(snapshot.data());
    if (!existingRecord) {
      throw new HttpsError("not-found", "Tracked case not found.");
    }

    const now = Date.now();
    const lastCheckedAt = existingRecord.lastCheckedAt || 0;
    if (now - lastCheckedAt < MANUAL_REFRESH_COOLDOWN_MS) {
      return {
        refreshed: false,
        statusChanged: false,
        cooldownRemainingMinutes: Math.max(
          1,
          Math.ceil((MANUAL_REFRESH_COOLDOWN_MS - (now - lastCheckedAt)) / 60_000)
        ),
        caseId,
        tracker: existingRecord,
      };
    }

    const refreshResult = await refreshTrackerRecord({
      userId,
      receiptNumber: caseId,
      existingRecord,
      bypassCooldown: true,
      shouldNotifyOnChange: true,
    });
    return {
      refreshed: true,
      statusChanged: refreshResult.statusChanged,
      cooldownRemainingMinutes: 0,
      caseId,
      tracker: refreshResult.tracker,
    };
  }
);

export const archiveUscisCase = onCall(
  {enforceAppCheck: true},
  async (request) => {
    const userId = requireAuth(request.auth);
    const caseId = normalizeReceiptNumber(request.data.caseId);
    await trackerDocRef(userId, caseId).set(
      {
        isArchived: true,
        nextPollAt: Date.now() + FAILURE_BACKOFF_INTERVAL_MS,
      },
      {merge: true}
    );
    return {caseId};
  }
);

export const removeUscisCase = onCall(
  {enforceAppCheck: true},
  async (request) => {
    const userId = requireAuth(request.auth);
    const caseId = normalizeReceiptNumber(request.data.caseId);
    await trackerDocRef(userId, caseId).delete();
    return {caseId};
  }
);

export const syncUscisCaseTrackerDevice = onCall(
  {enforceAppCheck: true},
  async (request) => {
    const userId = requireAuth(request.auth);
    const installationId = asNonEmptyString(request.data.installationId, "installationId");
    const enabled = request.data.enabled !== false;
    const token = asOptionalString(request.data.token);
    const endpointRef = notificationDeviceRef(userId, installationId);
    const current = await endpointRef.get();
    const existing = toNotificationDeviceRecord(current.data());
    if (!enabled || !token) {
      if (!existing?.policyAlertsEnabled) {
        await endpointRef.delete().catch(() => undefined);
        return {enabled: false};
      }
      await endpointRef.set(
        {
          caseStatusEnabled: false,
          enabled: existing.policyAlertsEnabled,
          updatedAt: Date.now(),
        },
        {merge: true}
      );
      return {enabled: false};
    }

    const now = Date.now();
    const payload: NotificationDeviceRecord = {
      installationId,
      token,
      platform: DEVICE_PLATFORM,
      enabled: true,
      caseStatusEnabled: true,
      policyAlertsEnabled: existing?.policyAlertsEnabled ?? false,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };
    await endpointRef.set(payload, {merge: true});
    return {enabled: true};
  }
);

export const pollTrackedUscisCases = onSchedule(
  {
    schedule: "every 12 hours",
    timeZone: "Etc/UTC",
    secrets: [uscisClientId, uscisClientSecret],
  },
  async () => {
    if (buildTrackerAvailability().mode === "disabled") {
      logger.info("USCIS tracker polling skipped because the tracker is disabled.");
      return;
    }

    const now = Date.now();
    const dueSnapshot = await firestore
      .collectionGroup("uscisCases")
      .where("isArchived", "==", false)
      .where("isTerminal", "==", false)
      .where("nextPollAt", "<=", now)
      .limit(MAX_POLLED_CASES_PER_RUN)
      .get();

    for (const doc of dueSnapshot.docs) {
      const userId = doc.ref.parent.parent?.id;
      if (!userId) {
        continue;
      }
      const tracker = toTrackerRecord(doc.data());
      if (!tracker) {
        continue;
      }

      try {
        await refreshTrackerRecord({
          userId,
          receiptNumber: doc.id,
          existingRecord: tracker,
          bypassCooldown: true,
          shouldNotifyOnChange: true,
        });
      } catch (error) {
        logger.error("Scheduled USCIS poll failed", {
          userId,
          caseId: doc.id,
          error: error instanceof Error ? error.message : `${error}`,
        });
      }
    }
  }
);

async function refreshTrackerRecord(input: {
  userId: string;
  receiptNumber: string;
  existingRecord: UscisCaseTrackerRecord | null;
  bypassCooldown: boolean;
  shouldNotifyOnChange: boolean;
}): Promise<{tracker: UscisCaseTrackerRecord; statusChanged: boolean}> {
  const now = Date.now();
  try {
    const parsedCase = await fetchUscisCaseStatus(input.receiptNumber);
    if (normalizeFormType(parsedCase.formType) != "I-765") {
      throw new HttpsError(
        "failed-precondition",
        "Only Form I-765 receipt numbers are supported in this version."
      );
    }

    const normalizedStage = normalizeI765Stage(
      parsedCase.officialStatusText,
      parsedCase.officialStatusDescription
    );
    const deterministicAction = buildDeterministicRecommendedAction(normalizedStage);
    const aiInterpretation = await interpretCaseStatus(parsedCase, normalizedStage, deterministicAction);
    const statusHash = computeStatusHash(
      parsedCase.officialStatusText,
      parsedCase.officialStatusDescription,
      parsedCase.modifiedAt
    );
    const statusChanged = Boolean(input.existingRecord && input.existingRecord.statusHash != statusHash);
    const lastChangedAt = statusChanged || !input.existingRecord ? now : input.existingRecord.lastChangedAt;
    const tracker: UscisCaseTrackerRecord = {
      receiptNumber: input.receiptNumber,
      formType: "I-765",
      normalizedStage,
      officialStatusText: parsedCase.officialStatusText,
      officialStatusDescription: parsedCase.officialStatusDescription,
      officialHistory: parsedCase.history,
      plainEnglishSummary: aiInterpretation.plainEnglishSummary,
      recommendedAction: deterministicAction,
      classification: coerceInterpretationClassification(normalizedStage, aiInterpretation),
      confidence: aiInterpretation.confidence,
      watchFor: aiInterpretation.watchFor,
      statusHash,
      lastCheckedAt: now,
      lastChangedAt,
      nextPollAt: now + DEFAULT_POLL_INTERVAL_MS,
      lastError: "",
      consecutiveFailureCount: 0,
      isTerminal: isTerminalStage(normalizedStage),
      isArchived: false,
    };

    await trackerDocRef(input.userId, input.receiptNumber).set(tracker, {merge: true});
    if (statusChanged && input.shouldNotifyOnChange) {
      await sendStatusChangedNotifications(input.userId, tracker);
    }
    return {tracker, statusChanged};
  } catch (error) {
    const errorMessage = toAppSafeError(error);
    logger.error("USCIS tracker refresh failed", {
      userId: input.userId,
      receiptNumber: input.receiptNumber,
      error: errorMessage,
    });

    if (input.existingRecord) {
      const failureCount = input.existingRecord.consecutiveFailureCount + 1;
      const updated = {
        lastError: errorMessage,
        consecutiveFailureCount: failureCount,
        lastCheckedAt: now,
        nextPollAt: now + (failureCount >= FAILURE_BACKOFF_THRESHOLD ?
          FAILURE_BACKOFF_INTERVAL_MS :
          DEFAULT_POLL_INTERVAL_MS),
      };
      await trackerDocRef(input.userId, input.receiptNumber).set(updated, {merge: true});
      return {
        tracker: {
          ...input.existingRecord,
          ...updated,
        },
        statusChanged: false,
      };
    }

    throw new HttpsError("unavailable", errorMessage);
  }
}

async function fetchUscisCaseStatus(receiptNumber: string): Promise<ParsedUscisCaseResponse> {
  const mode = getTrackerMode();
  const accessToken = await getUscisAccessToken(mode);
  const response = await fetch(buildCaseStatusUrl(mode, receiptNumber), {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    if (response.status == 404) {
      throw new Error("USCIS could not find that receipt number. Check it and try again.");
    }
    logger.error("USCIS Case Status API failed", {
      status: response.status,
      body: errorText,
    });
    throw new Error("USCIS case status is temporarily unavailable. Try again later.");
  }

  const rawPayload = await response.json() as unknown;
  return parseUscisCasePayload(rawPayload, receiptNumber);
}

async function getUscisAccessToken(mode: TrackerMode): Promise<string> {
  const now = Date.now();
  if (
    cachedAccessToken &&
    cachedAccessToken.mode === mode &&
    cachedAccessToken.expiresAt > now + 60_000
  ) {
    return cachedAccessToken.accessToken;
  }

  const clientId = uscisClientId.value();
  const clientSecret = uscisClientSecret.value();
  if (!clientId || !clientSecret) {
    throw new Error("USCIS tracker credentials are not configured.");
  }

  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
  const response = await fetch(buildTokenUrl(mode), {
    method: "POST",
    headers: {
      Authorization: `Basic ${credentials}`,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: "grant_type=client_credentials",
  });

  if (!response.ok) {
    logger.error("USCIS OAuth token request failed", {
      status: response.status,
      body: await response.text(),
    });
    throw new Error("Unable to authenticate with USCIS case status.");
  }

  const payload = await response.json() as Record<string, unknown>;
  const accessToken = asNonEmptyString(payload.access_token, "access_token");
  const expiresInSeconds = existingNumber(payload.expires_in) ?? 1800;
  cachedAccessToken = {
    mode,
    accessToken,
    expiresAt: now + (expiresInSeconds * 1000),
  };
  return accessToken;
}

async function interpretCaseStatus(
  parsedCase: ParsedUscisCaseResponse,
  normalizedStage: CaseStage,
  deterministicAction: string
): Promise<InterpretationResult> {
  const prompt = `
You rewrite official USCIS case status text into plain English for an F-1 student using Optional Practical Training.

You must not give legal advice. Stick to the official status. If the status is risky or unclear, classify as consult_dso_attorney.

Official status text:
- Source label: ${TRACKER_SOURCE_LABEL}
- Source URL: ${TRACKER_SOURCE_URL}
- Form: ${parsedCase.formType}
- Stage: ${normalizedStage}
- Status title: ${parsedCase.officialStatusText}
- Status description: ${parsedCase.officialStatusDescription}
- Deterministic recommended action: ${deterministicAction}

Return JSON only.
  `.trim();

  try {
    const response = await model.generateContent({
      contents: [{role: "user", parts: [{text: prompt}]}],
      generationConfig: {
        responseMimeType: "application/json",
        responseSchema: {
          type: SchemaType.OBJECT,
          required: ["plainEnglishSummary", "confidence", "classification", "watchFor"],
          properties: {
            plainEnglishSummary: {type: SchemaType.STRING},
            confidence: {
              type: SchemaType.STRING,
              enum: ["low", "medium", "high"],
            },
            classification: {
              type: SchemaType.STRING,
              enum: ["informational", "consult_dso_attorney"],
            },
            watchFor: {
              type: SchemaType.ARRAY,
              items: {type: SchemaType.STRING},
            },
          },
        },
        maxOutputTokens: 400,
      },
    });
    const text = response.response.candidates?.[0]?.content?.parts?.[0]?.text || "{}";
    const json = JSON.parse(text) as Record<string, unknown>;
    return {
      plainEnglishSummary:
        asOptionalString(json.plainEnglishSummary) ||
        buildFallbackSummary(parsedCase.officialStatusText, deterministicAction),
      confidence: normalizeConfidence(asOptionalString(json.confidence)),
      classification: normalizeInterpretationClassification(asOptionalString(json.classification)),
      watchFor: toStringList(json.watchFor),
    };
  } catch (error) {
    logger.error("USCIS status interpretation failed", {
      error: error instanceof Error ? error.message : `${error}`,
    });
    return {
      plainEnglishSummary: buildFallbackSummary(parsedCase.officialStatusText, deterministicAction),
      confidence: "medium",
      classification: "informational",
      watchFor: [],
    };
  }
}

async function sendStatusChangedNotifications(
  userId: string,
  tracker: UscisCaseTrackerRecord
): Promise<void> {
  const snapshot = await deviceEndpointCollection(userId)
    .where("caseStatusEnabled", "==", true)
    .get();

  for (const doc of snapshot.docs) {
    const endpoint = toNotificationDeviceRecord(doc.data());
    if (!endpoint?.token) {
      continue;
    }

    try {
      await admin.messaging().send({
        token: endpoint.token,
        data: {
          type: USCIS_NOTIFICATION_TYPE,
          caseId: tracker.receiptNumber,
          title: "USCIS case update",
          body: "Your USCIS case status changed.",
        },
        android: {
          priority: "high",
        },
      });
    } catch (error) {
      const code = (error as {code?: string})?.code || "";
      logger.error("Failed to send USCIS case update notification", {
        userId,
        caseId: tracker.receiptNumber,
        installationId: endpoint.installationId,
        code,
      });
      if (code.includes("registration-token-not-registered") || code.includes("invalid-registration-token")) {
        await doc.ref.delete().catch(() => undefined);
      }
    }
  }
}

function parseUscisCasePayload(rawPayload: unknown, fallbackReceiptNumber: string): ParsedUscisCaseResponse {
  const record = extractFirstCaseRecord(rawPayload);
  const receiptNumber = normalizeReceiptNumber(
    firstString(record, "receiptNumber", "receipt_num", "receiptNum", "receipt_number") ||
      fallbackReceiptNumber
  );
  const formType = firstString(record, "formType", "form_num", "formNumber", "form_type") || "";
  const officialStatusText = sanitizeHtmlText(
    firstString(
      record,
      "currentCaseStatusText",
      "current_case_status_text",
      "currentCaseStatus",
      "current_case_status",
      "status"
    ) || "Status unavailable"
  );
  const officialStatusDescription = sanitizeHtmlText(
    firstString(
      record,
      "currentCaseStatusDescription",
      "current_case_status_desc",
      "current_case_status_desc_en",
      "currentCaseStatusDesc",
      "description"
    ) || "USCIS did not return a status description."
  );
  const modifiedAt = parseDateValue(
    firstValue(record, "modifiedDt", "modified_dt", "modifiedDate", "modified_date")
  );
  const history = parseHistory(
    firstValue(record, "histCaseStatus", "hist_case_status", "statusHistory", "history")
  );

  return {
    receiptNumber,
    formType: normalizeFormType(formType),
    officialStatusText,
    officialStatusDescription,
    modifiedAt,
    history,
  };
}

function extractFirstCaseRecord(rawPayload: unknown): Record<string, unknown> {
  if (Array.isArray(rawPayload)) {
    return toRecord(rawPayload[0]);
  }
  const payload = toRecord(rawPayload);
  const listValue =
    firstValue(payload, "cases", "case_statuses", "caseStatuses", "CaseStatusResponse");
  if (Array.isArray(listValue) && listValue.length > 0) {
    return toRecord(listValue[0]);
  }
  return payload;
}

function parseHistory(rawValue: unknown): UscisCaseHistoryEntry[] {
  if (!Array.isArray(rawValue)) {
    return [];
  }
  return rawValue.map((item) => {
    const record = toRecord(item);
    return {
      statusText: sanitizeHtmlText(
        firstString(record, "statusText", "status_text", "status", "case_status") || "Update"
      ),
      statusDescription: sanitizeHtmlText(
        firstString(record, "statusDescription", "status_desc", "description") || ""
      ),
      statusDate: parseDateValue(firstValue(record, "statusDate", "status_date", "modified_dt", "created_dt")),
    };
  });
}

function normalizeI765Stage(statusText: string, statusDescription: string): CaseStage {
  const normalized = `${statusText} ${statusDescription}`.toLowerCase();
  if (normalized.includes("actively reviewed")) return "ACTIVE_REVIEW";
  if (normalized.includes("fingerprint") || normalized.includes("biometric")) return "BIOMETRICS";
  if (normalized.includes("request for evidence") || normalized.includes("notice of intent to deny")) {
    return "RFE_OR_NOID";
  }
  if (normalized.includes("response to uscis' request for evidence was received") ||
    normalized.includes("correspondence was received")) {
    return "CORRESPONDENCE_RECEIVED";
  }
  if (normalized.includes("transferred")) return "TRANSFERRED";
  if (normalized.includes("new card is being produced") || normalized.includes("card is being produced")) {
    return "CARD_PRODUCED";
  }
  if (normalized.includes("approved")) return "APPROVED";
  if (normalized.includes("picked up by the united states postal service") ||
    normalized.includes("card was mailed to me")) {
    return "CARD_PICKED_UP";
  }
  if (normalized.includes("delivered")) return "CARD_DELIVERED";
  if (normalized.includes("denied")) return "DENIED";
  if (normalized.includes("rejected")) return "REJECTED";
  if (normalized.includes("withdrawn") || normalized.includes("withdrawal")) return "WITHDRAWN";
  if (normalized.includes("received")) return "RECEIVED";
  return "UNKNOWN";
}

function buildDeterministicRecommendedAction(stage: CaseStage): string {
  switch (stage) {
  case "RECEIVED":
    return "Keep your receipt notice and monitor USCIS for the next update.";
  case "ACTIVE_REVIEW":
    return "No action is usually required right now. Watch your mail and USCIS account for new requests.";
  case "BIOMETRICS":
    return "Follow the biometrics notice exactly and attend the appointment if USCIS scheduled one.";
  case "RFE_OR_NOID":
    return "Review the notice carefully and contact your DSO or an immigration attorney before responding.";
  case "CORRESPONDENCE_RECEIVED":
    return "USCIS received your response. Keep monitoring for the next status update.";
  case "TRANSFERRED":
    return "USCIS moved your case internally. Keep monitoring for the next update.";
  case "APPROVED":
    return "Your case appears approved. Watch for card-production and delivery updates.";
  case "CARD_PRODUCED":
    return "USCIS is producing your EAD card. Watch for mailing and delivery updates.";
  case "CARD_PICKED_UP":
    return "Your EAD appears to be in transit. Track delivery and keep your mailing address current.";
  case "CARD_DELIVERED":
    return "Confirm you received the EAD card and keep it with your OPT records.";
  case "DENIED":
    return "Do not rely on OPT work authorization based on this status. Contact your DSO or an immigration attorney.";
  case "REJECTED":
    return "USCIS rejected the filing. Contact your DSO or an immigration attorney before taking action.";
  case "WITHDRAWN":
    return "The filing appears withdrawn. Confirm the situation with your DSO before taking action.";
  case "UNKNOWN":
    return "Review the official USCIS text carefully and confirm anything unclear with your DSO.";
  }
}

function isTerminalStage(stage: CaseStage): boolean {
  return stage === "CARD_DELIVERED" ||
    stage === "DENIED" ||
    stage === "REJECTED" ||
    stage === "WITHDRAWN";
}

function buildFallbackSummary(statusText: string, recommendedAction: string): string {
  return `${statusText}. ${recommendedAction}`.trim();
}

function coerceInterpretationClassification(
  stage: CaseStage,
  interpretation: InterpretationResult
): InterpretationClassification {
  if (
    stage === "RFE_OR_NOID" ||
    stage === "DENIED" ||
    stage === "REJECTED" ||
    stage === "WITHDRAWN" ||
    interpretation.confidence === "low"
  ) {
    return "consult_dso_attorney";
  }
  return interpretation.classification;
}

function computeStatusHash(statusText: string, statusDescription: string, modifiedAt: number | null): string {
  return createHash("sha256")
    .update(`${statusText}|${statusDescription}|${modifiedAt ?? ""}`)
    .digest("hex");
}

function sanitizeHtmlText(rawValue: string): string {
  return rawValue
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/\s+/g, " ")
    .trim();
}

function buildTrackerAvailability(): AvailabilityResponse {
  const mode = getTrackerMode();
  if (mode === "disabled") {
    return {
      mode,
      reason: "Case tracking is not enabled for this environment yet.",
      maxTrackedCases: MAX_TRACKED_CASES,
    };
  }
  return {
    mode,
    reason: "",
    maxTrackedCases: MAX_TRACKED_CASES,
  };
}

function ensureTrackerEnabled(): void {
  const availability = buildTrackerAvailability();
  if (availability.mode === "disabled") {
    throw new HttpsError("failed-precondition", availability.reason);
  }
}

function getTrackerMode(): TrackerMode {
  const raw = (process.env.USCIS_TRACKER_MODE || "disabled").toLowerCase().trim();
  if (raw === "sandbox" || raw === "production") {
    return raw;
  }
  return "disabled";
}

function buildTokenUrl(mode: TrackerMode): string {
  const configured = process.env.USCIS_TORCH_TOKEN_URL?.trim();
  if (configured) {
    return configured;
  }
  return mode === "production" ?
    "https://api.uscis.gov/oauth/accesstoken" :
    "https://api-int.uscis.gov/oauth/accesstoken";
}

function buildCaseStatusUrl(mode: TrackerMode, receiptNumber: string): string {
  const configured = process.env.USCIS_CASE_STATUS_ENDPOINT?.trim();
  if (configured) {
    return configured.includes("{receiptNumber}") ?
      configured.replace("{receiptNumber}", encodeURIComponent(receiptNumber)) :
      `${configured.replace(/\/$/, "")}/${encodeURIComponent(receiptNumber)}`;
  }
  const baseUrl = mode === "production" ?
    "https://api.uscis.gov/case-status" :
    "https://api-int.uscis.gov/case-status";
  return `${baseUrl}/${encodeURIComponent(receiptNumber)}`;
}

function requireAuth(auth: {uid: string} | undefined): string {
  if (!auth?.uid) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  return auth.uid;
}

function trackerCollection(userId: string) {
  return firestore.collection("users").doc(userId).collection("uscisCases");
}

function trackerDocRef(userId: string, receiptNumber: string) {
  return trackerCollection(userId).doc(receiptNumber);
}

function deviceEndpointCollection(userId: string) {
  return firestore.collection("users").doc(userId).collection("notificationDevices");
}

function notificationDeviceRef(userId: string, installationId: string) {
  return deviceEndpointCollection(userId).doc(installationId);
}

function toTrackerRecord(value: unknown): UscisCaseTrackerRecord | null {
  const record = toRecord(value);
  if (!record.receiptNumber) {
    return null;
  }
  return {
    receiptNumber: asOptionalString(record.receiptNumber) || "",
    formType: asOptionalString(record.formType) || "",
    normalizedStage: normalizeStoredStage(asOptionalString(record.normalizedStage)),
    officialStatusText: asOptionalString(record.officialStatusText) || "",
    officialStatusDescription: asOptionalString(record.officialStatusDescription) || "",
    officialHistory: parseHistory(record.officialHistory),
    plainEnglishSummary: asOptionalString(record.plainEnglishSummary) || "",
    recommendedAction: asOptionalString(record.recommendedAction) || "",
    classification: normalizeInterpretationClassification(asOptionalString(record.classification)),
    confidence: normalizeConfidence(asOptionalString(record.confidence)),
    watchFor: toStringList(record.watchFor),
    statusHash: asOptionalString(record.statusHash) || "",
    lastCheckedAt: existingNumber(record.lastCheckedAt) || 0,
    lastChangedAt: existingNumber(record.lastChangedAt) || 0,
    nextPollAt: existingNumber(record.nextPollAt) || 0,
    lastError: asOptionalString(record.lastError) || "",
    consecutiveFailureCount: existingNumber(record.consecutiveFailureCount) || 0,
    isTerminal: Boolean(record.isTerminal),
    isArchived: Boolean(record.isArchived),
  };
}

function toNotificationDeviceRecord(value: unknown): NotificationDeviceRecord | null {
  const record = toRecord(value);
  const installationId = asOptionalString(record.installationId);
  const token = asOptionalString(record.token);
  if (!installationId || !token) {
    return null;
  }
  return {
    installationId,
    token,
    platform: asOptionalString(record.platform) || DEVICE_PLATFORM,
    enabled: record.enabled !== false,
    caseStatusEnabled: record.caseStatusEnabled !== false,
    policyAlertsEnabled: Boolean(record.policyAlertsEnabled),
    createdAt: existingNumber(record.createdAt) || 0,
    updatedAt: existingNumber(record.updatedAt) || 0,
  };
}

function normalizeReceiptNumber(value: unknown): string {
  const raw = asNonEmptyString(value, "receiptNumber").toUpperCase();
  if (!/^[A-Z]{3}[0-9]{10}$/.test(raw)) {
    throw new HttpsError("invalid-argument", "Receipt number must match ABC1234567890.");
  }
  return raw;
}

function normalizeFormType(value: string): string {
  const normalized = value.toUpperCase().replace(/\s+/g, "");
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("I765")) {
    return "I-765";
  }
  return value.toUpperCase();
}

function normalizeStoredStage(value: string | null): CaseStage {
  switch (value) {
  case "RECEIVED":
  case "ACTIVE_REVIEW":
  case "BIOMETRICS":
  case "RFE_OR_NOID":
  case "CORRESPONDENCE_RECEIVED":
  case "TRANSFERRED":
  case "APPROVED":
  case "CARD_PRODUCED":
  case "CARD_PICKED_UP":
  case "CARD_DELIVERED":
  case "DENIED":
  case "REJECTED":
  case "WITHDRAWN":
    return value;
  default:
    return "UNKNOWN";
  }
}

function normalizeInterpretationClassification(
  value: string | null
): InterpretationClassification {
  return value === "consult_dso_attorney" ? "consult_dso_attorney" : "informational";
}

function normalizeConfidence(value: string | null): InterpretationConfidence {
  if (value === "high" || value === "medium") {
    return value;
  }
  return "low";
}

function toAppSafeError(error: unknown): string {
  if (error instanceof HttpsError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unable to refresh USCIS case status right now.";
}

function parseDateValue(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value > 9_999_999_999 ? value : value * 1000;
  }
  const raw = asOptionalString(value);
  if (!raw) {
    return null;
  }
  const asNumber = Number(raw);
  if (Number.isFinite(asNumber)) {
    return asNumber > 9_999_999_999 ? asNumber : asNumber * 1000;
  }
  const parsed = Date.parse(raw);
  return Number.isNaN(parsed) ? null : parsed;
}

function firstValue(record: Record<string, unknown>, ...aliases: string[]): unknown {
  for (const alias of aliases) {
    const direct = record[alias];
    if (direct !== undefined && direct !== null) {
      return direct;
    }
    const snake = alias.replace(/[A-Z]/g, (value) => `_${value.toLowerCase()}`);
    const snakeValue = record[snake];
    if (snakeValue !== undefined && snakeValue !== null) {
      return snakeValue;
    }
  }
  return null;
}

function firstString(record: Record<string, unknown>, ...aliases: string[]): string | null {
  const value = firstValue(record, ...aliases);
  return asOptionalString(value);
}

function toRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function toStringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}

function existingNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function asOptionalString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed || null;
}

function asNonEmptyString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new HttpsError("invalid-argument", `${fieldName} is required.`);
  }
  return value.trim();
}

export {
  buildDeterministicRecommendedAction,
  computeStatusHash,
  normalizeI765Stage,
  normalizeReceiptNumber,
  parseUscisCasePayload,
  sanitizeHtmlText,
};
