import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import * as cheerio from "cheerio";
import { createHash } from "crypto";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { firestore } from "./documentSecurity";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const DEVICE_PLATFORM = "android";
const POLICY_ALERT_NOTIFICATION_TYPE = "policy_alert_published";
const POLICY_ALERT_SCHEDULE = "every 6 hours";
const POLICY_ALERT_SOURCE_WINDOW_DAYS = 365;
const FEDERAL_REGISTER_API_BASE = "https://www.federalregister.gov/api/v1/documents.json";
const USCIS_ALERTS_URL = "https://www.uscis.gov/alerts";
const USCIS_POLICY_UPDATES_URL = "https://www.uscis.gov/policy-manual/updates";
const ICE_SEVIS_WHATS_NEW_URL = "https://www.ice.gov/sevis/whats-new";
const STUDY_IN_THE_STATES_URL = "https://studyinthestates.dhs.gov/";
const DOS_VISAS_NEWS_URL = "https://travel.state.gov/content/travel/en/News/visas-news.html";
const WHITE_HOUSE_PRESIDENTIAL_ACTIONS_URL = "https://www.whitehouse.gov/presidential-actions/";

const POLICY_ALERT_CANDIDATE_STATUSES = [
  "needs_review",
  "approved",
  "dismissed",
  "published",
] as const;
const POLICY_ALERT_SEVERITIES = ["critical", "high", "medium", "low"] as const;
const POLICY_ALERT_CONFIDENCES = ["high", "medium", "low"] as const;
const POLICY_ALERT_FINALITIES = [
  "final",
  "guidance",
  "operations",
  "proposal",
  "executive_action",
  "litigation",
] as const;
const POLICY_ALERT_TOPICS = ["travel", "employment", "reporting", "applications"] as const;
const POLICY_ALERT_AUDIENCES = ["all", "initial_opt", "stem_opt", "all_opt_users"] as const;

const HIGHLIGHT_CRITICAL_TERMS = [
  "fixed time period of admission",
  "duration of status",
  "restricting and limiting the entry of foreign nationals",
  "suspension of visa issuance",
  "travel ban",
  "proclamation",
];

const RELEVANCE_PATTERNS = [
  /\boptional practical training\b/i,
  /\bstem opt\b/i,
  /\bstem extension\b/i,
  /\bcap-gap\b/i,
  /\bsevis\b/i,
  /\bsevp\b/i,
  /\bf-1\b/i,
  /\bi-20\b/i,
  /\bi-983\b/i,
  /\bpractical training\b/i,
  /\bstudent and exchange visitor\b/i,
  /\binterview waiver\b/i,
  /\bcountry of residence\b/i,
  /\bnonimmigrant visa\b/i,
  /\bvisa issuance\b/i,
];

type PolicyAlertCandidateStatus = typeof POLICY_ALERT_CANDIDATE_STATUSES[number];
type PolicyAlertSeverity = typeof POLICY_ALERT_SEVERITIES[number];
type PolicyAlertConfidence = typeof POLICY_ALERT_CONFIDENCES[number];
type PolicyAlertFinality = typeof POLICY_ALERT_FINALITIES[number];
type PolicyAlertTopic = typeof POLICY_ALERT_TOPICS[number];
type PolicyAlertAudience = typeof POLICY_ALERT_AUDIENCES[number];

type PolicyAlertSourceSignal = {
  sourceLabel: string;
  sourceUrl: string;
  title: string;
  summary: string;
  bodyText: string;
  sourcePublishedAt: number | null;
  effectiveDate: number | null;
  effectiveDateText: string | null;
  documentNumber: string | null;
  finalityHint: PolicyAlertFinality | null;
  topicsHint: PolicyAlertTopic[];
  audienceHint: PolicyAlertAudience | null;
  callToActionRoute: string | null;
  callToActionLabel: string | null;
};

type PolicyAlertSourceRecord = {
  label: string;
  url: string;
  publishedAt: number | null;
};

type PolicyAlertCandidateRecord = {
  id: string;
  status: PolicyAlertCandidateStatus;
  title: string;
  whatChanged: string;
  effectiveDate: number | null;
  effectiveDateText: string | null;
  whoIsAffected: string;
  whyItMatters: string;
  recommendedAction: string;
  source: PolicyAlertSourceRecord;
  lastReviewedAt: number | null;
  severity: PolicyAlertSeverity;
  confidence: PolicyAlertConfidence;
  finality: PolicyAlertFinality;
  topics: PolicyAlertTopic[];
  audience: PolicyAlertAudience;
  affectedOptTypes: string[];
  supersedesAlertId: string | null;
  supersededByAlertId: string | null;
  isArchived: boolean;
  isSuperseded: boolean;
  sendPush: boolean;
  urgentPush: boolean;
  dedupeKey: string;
  sourceHash: string;
  sourceDomain: string;
  documentNumber: string | null;
  firstDetectedAt: number;
  lastDetectedAt: number;
  publishedAt: number | null;
  publishedBy: string;
  reviewedBy: string;
  callToActionRoute: string | null;
  callToActionLabel: string | null;
  rawSourceSnapshot: Record<string, unknown>;
  publishError: string;
};

type PolicyAlertPublishedRecord = Omit<
  PolicyAlertCandidateRecord,
  "status" | "firstDetectedAt" | "lastDetectedAt" | "rawSourceSnapshot" | "publishError" | "reviewedBy"
> & {
  candidateId: string;
  updatedAt: number;
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

export const syncPolicyAlertCandidates = onSchedule(
  {
    schedule: POLICY_ALERT_SCHEDULE,
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Date.now();
    const signals = await collectPolicyAlertSignals();
    const candidates = dedupeCandidates([
      ...buildSeedPolicyAlertCandidates(now),
      ...signals
        .filter(isLikelyPolicyAlertRelevant)
        .map((signal) => buildPolicyAlertCandidateFromSignal(signal, now)),
    ]);
    for (const candidate of candidates) {
      try {
        await upsertPolicyAlertCandidate(candidate);
      } catch (error) {
        logger.error("Policy alert candidate upsert failed", {
          candidateId: candidate.id,
          error: error instanceof Error ? error.message : `${error}`,
        });
      }
    }
  }
);

export const syncNotificationDevice = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const userId = requireAuth(request.auth);
    const installationId = asNonEmptyString(request.data.installationId, "installationId");
    const token = asOptionalString(request.data.token);
    const caseStatusEnabled = asOptionalBoolean(request.data.caseStatusEnabled);
    const policyAlertsEnabled = asOptionalBoolean(request.data.policyAlertsEnabled);
    return upsertNotificationDevice(
      userId,
      installationId,
      token,
      caseStatusEnabled,
      policyAlertsEnabled
    );
  }
);

export const publishApprovedPolicyAlertCandidate = onDocumentWritten(
  "policyAlertCandidates/{candidateId}",
  async (event) => {
    const afterSnapshot = event.data?.after;
    const beforeSnapshot = event.data?.before;
    if (!afterSnapshot?.exists) {
      return;
    }

    const candidate = toPolicyAlertCandidateRecord(
      event.params.candidateId,
      afterSnapshot.data()
    );
    if (candidate.status !== "approved") {
      return;
    }

    const previous = beforeSnapshot?.exists ?
      toPolicyAlertCandidateRecord(event.params.candidateId, beforeSnapshot.data()) :
      null;
    if (previous?.status === "published") {
      return;
    }

    try {
      validateCandidateForPublish(candidate);
      const publishedAt = candidate.publishedAt ?? Date.now();
      const publishedBy = candidate.reviewedBy || "firebase-console";
      const publishedAlert = buildPublishedPolicyAlert(candidate, publishedAt, publishedBy);
      const batch = firestore.batch();
      batch.set(policyAlertDocRef(publishedAlert.id), publishedAlert, { merge: true });
      batch.set(
        afterSnapshot.ref,
        {
          status: "published",
          publishedAt,
          publishedBy,
          publishError: "",
        },
        { merge: true }
      );
      if (publishedAlert.supersedesAlertId) {
        batch.set(
          policyAlertDocRef(publishedAlert.supersedesAlertId),
          {
            isSuperseded: true,
            supersededByAlertId: publishedAlert.id,
            updatedAt: publishedAt,
          },
          { merge: true }
        );
      }
      await batch.commit();

      if (shouldSendPolicyAlertPush(publishedAlert)) {
        await sendPolicyAlertNotifications(publishedAlert);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to publish this alert.";
      logger.error("Policy alert publish failed", {
        candidateId: candidate.id,
        error: message,
      });
      await afterSnapshot.ref.set(
        {
          status: "needs_review",
          publishError: message,
        },
        { merge: true }
      );
    }
  }
);

function policyAlertCandidateDocRef(candidateId: string) {
  return firestore.collection("policyAlertCandidates").doc(candidateId);
}

function policyAlertDocRef(alertId: string) {
  return firestore.collection("policyAlerts").doc(alertId);
}

function notificationDeviceDocRef(userId: string, installationId: string) {
  return firestore.collection("users").doc(userId).collection("notificationDevices").doc(installationId);
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

function asOptionalBoolean(value: unknown): boolean | null {
  return typeof value === "boolean" ? value : null;
}

function normalizeWhitespace(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function trimToLength(value: string, max: number): string {
  if (value.length <= max) {
    return value;
  }
  return `${value.slice(0, max - 1).trim()}…`;
}

function hostForUrl(url: string): string {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return "";
  }
}

function parseDateValue(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value > 9_999_999_999 ? value : value * 1000;
  }
  const raw = asOptionalString(value);
  if (!raw) {
    return null;
  }
  const numeric = Number(raw);
  if (Number.isFinite(numeric)) {
    return numeric > 9_999_999_999 ? numeric : numeric * 1000;
  }
  const parsed = Date.parse(raw);
  return Number.isNaN(parsed) ? null : parsed;
}

function daysAgoIso(days: number): string {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

function requireAuth(auth: { uid: string } | undefined): string {
  if (!auth?.uid) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  return auth.uid;
}

async function collectPolicyAlertSignals(): Promise<PolicyAlertSourceSignal[]> {
  const results = await Promise.allSettled([
    fetchFederalRegisterSignals(),
    fetchHtmlPolicySignals(USCIS_ALERTS_URL, "USCIS Alerts", "/alerts"),
    fetchHtmlPolicySignals(USCIS_POLICY_UPDATES_URL, "USCIS Policy Manual Updates", "/policy-manual/updates"),
    fetchHtmlPolicySignals(ICE_SEVIS_WHATS_NEW_URL, "ICE SEVIS What's New", "/sevis/"),
    fetchHtmlPolicySignals(STUDY_IN_THE_STATES_URL, "Study in the States", "studyinthestates.dhs.gov"),
    fetchHtmlPolicySignals(DOS_VISAS_NEWS_URL, "DOS U.S. Visas News", "/News/visas-news"),
    fetchHtmlPolicySignals(
      WHITE_HOUSE_PRESIDENTIAL_ACTIONS_URL,
      "White House Presidential Actions",
      "/presidential-actions/"
    ),
  ]);

  return results.flatMap((result) => {
    if (result.status === "fulfilled") {
      return result.value;
    }
    logger.error("Policy alert source collection failed", {
      error: result.reason instanceof Error ? result.reason.message : `${result.reason}`,
    });
    return [];
  });
}

async function fetchFederalRegisterSignals(): Promise<PolicyAlertSourceSignal[]> {
  const queries = [
    "\"optional practical training\"",
    "\"student and exchange visitor\"",
    "\"fixed time period of admission\"",
    "\"visa issuance\"",
  ];
  const items: PolicyAlertSourceSignal[] = [];
  for (const term of queries) {
    const url = new URL(FEDERAL_REGISTER_API_BASE);
    url.searchParams.set("per_page", "20");
    url.searchParams.set("order", "newest");
    url.searchParams.set("conditions[publication_date][gte]", daysAgoIso(POLICY_ALERT_SOURCE_WINDOW_DAYS));
    url.searchParams.append("conditions[type][]", "RULE");
    url.searchParams.append("conditions[type][]", "PRORULE");
    url.searchParams.append("conditions[type][]", "NOTICE");
    url.searchParams.append("conditions[type][]", "PRESDOCU");
    url.searchParams.set("conditions[term]", term);
    const response = await fetch(url.toString());
    if (!response.ok) {
      throw new Error(`Federal Register request failed with ${response.status}.`);
    }
    const payload = await response.json() as Record<string, unknown>;
    items.push(...extractFederalRegisterSignalsFromResponse(payload));
  }
  return dedupeSignals(items);
}

async function fetchHtmlPolicySignals(
  pageUrl: string,
  sourceLabel: string,
  hrefContains: string
): Promise<PolicyAlertSourceSignal[]> {
  const response = await fetch(pageUrl, {
    headers: {
      "User-Agent": "OPTPal Policy Alert Feed",
    },
  });
  if (!response.ok) {
    throw new Error(`Policy source request failed for ${pageUrl} with ${response.status}.`);
  }
  const html = await response.text();
  return extractSignalsFromHtml(pageUrl, sourceLabel, html, hrefContains);
}

function extractFederalRegisterSignalsFromResponse(payload: Record<string, unknown>): PolicyAlertSourceSignal[] {
  const results = Array.isArray(payload.results) ? payload.results : [];
  return results.map((item) => {
    const record = toRecord(item);
    const title = asOptionalString(record.title) || "";
    const summary = asOptionalString(record.abstract) || asOptionalString(record.excerpt) || "";
    const sourceUrl = asOptionalString(record.html_url) || asOptionalString(record.pdf_url) || "";
    const type = `${asOptionalString(record.type) || ""}`.toUpperCase();
    return {
      sourceLabel: "Federal Register",
      sourceUrl,
      title,
      summary,
      bodyText: summary,
      sourcePublishedAt: parseDateValue(record.publication_date),
      effectiveDate: parseDateValue(record.effective_on),
      effectiveDateText: asOptionalString(record.effective_on),
      documentNumber: asOptionalString(record.document_number),
      finalityHint: inferFederalRegisterFinality(type),
      topicsHint: inferPolicyAlertTopics(`${title} ${summary}`),
      audienceHint: inferPolicyAlertAudience(`${title} ${summary}`),
      callToActionRoute: inferCallToActionRoute(`${title} ${summary}`),
      callToActionLabel: inferCallToActionLabel(`${title} ${summary}`),
    };
  }).filter((item) => item.title && item.sourceUrl);
}

function extractSignalsFromHtml(
  pageUrl: string,
  sourceLabel: string,
  html: string,
  hrefContains: string
): PolicyAlertSourceSignal[] {
  const $ = cheerio.load(html);
  const candidates: PolicyAlertSourceSignal[] = [];
  $("a[href]").each((_, element) => {
    const anchor = $(element);
    const href = anchor.attr("href");
    const title = normalizeWhitespace(anchor.text());
    if (!href || title.length < 12 || !href.includes(hrefContains)) {
      return;
    }
    const sourceUrl = new URL(href, pageUrl).toString();
    const container = anchor.closest("article, li, .views-row, .usa-collection__item, .news-item, section, div");
    const containerText = normalizeWhitespace(container.text());
    const summary = trimToLength(containerText.replace(title, "").trim(), 240);
    candidates.push({
      sourceLabel,
      sourceUrl,
      title,
      summary,
      bodyText: containerText,
      sourcePublishedAt: parseDateValue(findLikelyDate(containerText)),
      effectiveDate: null,
      effectiveDateText: null,
      documentNumber: null,
      finalityHint: inferHtmlSourceFinality(sourceLabel, `${title} ${containerText}`),
      topicsHint: inferPolicyAlertTopics(`${title} ${containerText}`),
      audienceHint: inferPolicyAlertAudience(`${title} ${containerText}`),
      callToActionRoute: inferCallToActionRoute(`${title} ${containerText}`),
      callToActionLabel: inferCallToActionLabel(`${title} ${containerText}`),
    });
  });
  return dedupeSignals(candidates).slice(0, 20);
}

function buildPolicyAlertCandidateFromSignal(
  signal: PolicyAlertSourceSignal,
  now: number
): PolicyAlertCandidateRecord {
  const sourceHash = createHash("sha256")
    .update(`${signal.title}|${signal.summary}|${signal.bodyText}`)
    .digest("hex");
  const dedupeKey = createHash("sha256")
    .update(`${signal.sourceUrl}|${signal.documentNumber || ""}|${sourceHash}`)
    .digest("hex");
  const bodyText = `${signal.title} ${signal.summary} ${signal.bodyText}`.trim();
  const topics = signal.topicsHint.length > 0 ? signal.topicsHint : inferPolicyAlertTopics(bodyText);
  const audience = signal.audienceHint || inferPolicyAlertAudience(bodyText);
  const finality = signal.finalityHint || inferPolicyAlertFinality(bodyText);
  const severity = inferPolicyAlertSeverity(bodyText, finality);
  const confidence = inferPolicyAlertConfidence(bodyText, finality);
  return {
    id: dedupeKey,
    status: "needs_review",
    title: signal.title,
    whatChanged: buildWhatChanged(signal),
    effectiveDate: signal.effectiveDate,
    effectiveDateText: signal.effectiveDateText,
    whoIsAffected: buildWhoIsAffected(audience, topics),
    whyItMatters: buildWhyItMatters(bodyText, topics),
    recommendedAction: buildRecommendedAction(topics, finality),
    source: {
      label: signal.sourceLabel,
      url: signal.sourceUrl,
      publishedAt: signal.sourcePublishedAt,
    },
    lastReviewedAt: null,
    severity,
    confidence,
    finality,
    topics,
    audience,
    affectedOptTypes: affectedOptTypesForAudience(audience),
    supersedesAlertId: null,
    supersededByAlertId: null,
    isArchived: false,
    isSuperseded: false,
    sendPush: severity === "critical" || (severity === "high" && confidence === "high"),
    urgentPush: severity === "critical",
    dedupeKey,
    sourceHash,
    sourceDomain: hostForUrl(signal.sourceUrl),
    documentNumber: signal.documentNumber,
    firstDetectedAt: now,
    lastDetectedAt: now,
    publishedAt: null,
    publishedBy: "",
    reviewedBy: "",
    callToActionRoute: signal.callToActionRoute,
    callToActionLabel: signal.callToActionLabel,
    rawSourceSnapshot: {
      title: signal.title,
      summary: signal.summary,
      bodyText: trimToLength(signal.bodyText, 2000),
    },
    publishError: "",
  };
}

function inferFederalRegisterFinality(value: string): PolicyAlertFinality {
  switch (value) {
  case "RULE":
    return "final";
  case "PRORULE":
    return "proposal";
  case "PRESDOCU":
    return "executive_action";
  default:
    return "guidance";
  }
}

function inferHtmlSourceFinality(sourceLabel: string, text: string): PolicyAlertFinality {
  const normalized = text.toLowerCase();
  if (sourceLabel.includes("White House")) return "executive_action";
  if (sourceLabel.includes("Policy Manual")) return "guidance";
  if (normalized.includes("proposed rule")) return "proposal";
  if (normalized.includes("waiver") || normalized.includes("operations")) return "operations";
  return "guidance";
}

function inferPolicyAlertFinality(text: string): PolicyAlertFinality {
  const normalized = text.toLowerCase();
  if (normalized.includes("proposed rule") || normalized.includes("proposal")) return "proposal";
  if (normalized.includes("proclamation") || normalized.includes("executive order")) return "executive_action";
  if (normalized.includes("lawsuit") || normalized.includes("litigation") || normalized.includes("court")) return "litigation";
  if (normalized.includes("interview waiver") || normalized.includes("operational") || normalized.includes("broadcast")) return "operations";
  if (normalized.includes("policy manual") || normalized.includes("guidance")) return "guidance";
  return "final";
}

function inferPolicyAlertSeverity(text: string, finality: PolicyAlertFinality): PolicyAlertSeverity {
  const normalized = text.toLowerCase();
  if (HIGHLIGHT_CRITICAL_TERMS.some((term) => normalized.includes(term))) {
    return "critical";
  }
  if (finality === "executive_action" || normalized.includes("travel restriction") || normalized.includes("suspend")) {
    return "high";
  }
  if (finality === "proposal" || finality === "litigation") {
    return "medium";
  }
  return "medium";
}

function inferPolicyAlertConfidence(text: string, finality: PolicyAlertFinality): PolicyAlertConfidence {
  if (finality === "proposal" || finality === "litigation") {
    return "low";
  }
  if (finality === "operations") {
    return "medium";
  }
  return "high";
}

function inferPolicyAlertTopics(text: string): PolicyAlertTopic[] {
  const normalized = text.toLowerCase();
  const topics = new Set<PolicyAlertTopic>();
  if (/(travel|visa|consular|reentry|country of residence|adjacent islands|interview waiver)/i.test(normalized)) {
    topics.add("travel");
  }
  if (/(employment|unemployment|stem opt|optional practical training|practical training|cap-gap)/i.test(normalized)) {
    topics.add("employment");
  }
  if (/(reporting|sevis|sevp|portal|i-983|dso)/i.test(normalized)) {
    topics.add("reporting");
  }
  if (/(i-765|uscis|application|filing|adjudication|policy manual|duration of status)/i.test(normalized)) {
    topics.add("applications");
  }
  if (topics.size === 0) {
    topics.add("applications");
  }
  return [...topics];
}

function inferPolicyAlertAudience(text: string): PolicyAlertAudience {
  const normalized = text.toLowerCase();
  if (normalized.includes("stem")) {
    return "stem_opt";
  }
  if (normalized.includes("post-completion") || normalized.includes("initial opt")) {
    return "initial_opt";
  }
  return "all_opt_users";
}

function buildWhatChanged(signal: PolicyAlertSourceSignal): string {
  return trimToLength(signal.summary || signal.title, 280);
}

function buildWhoIsAffected(audience: PolicyAlertAudience, topics: PolicyAlertTopic[]): string {
  const base = audience === "stem_opt" ?
    "Students on STEM OPT." :
    audience === "initial_opt" ?
      "Students on post-completion OPT." :
      "Students on post-completion OPT and STEM OPT.";
  if (topics.includes("travel")) {
    return `${base} This alert is especially relevant if international travel or visa renewal is involved.`;
  }
  return base;
}

function buildWhyItMatters(text: string, topics: PolicyAlertTopic[]): string {
  if (topics.includes("travel")) {
    return "This can change travel timing, visa-renewal strategy, or reentry risk for students who leave the United States.";
  }
  if (topics.includes("reporting")) {
    return "This can change what students or schools need to report or monitor to maintain status.";
  }
  if (topics.includes("employment")) {
    return "This can change whether students can work, how unemployment time is counted, or what evidence is required.";
  }
  return trimToLength(text, 220);
}

function buildRecommendedAction(topics: PolicyAlertTopic[], finality: PolicyAlertFinality): string {
  if (topics.includes("travel")) {
    return "Review the official source and re-check your travel plan before leaving the United States.";
  }
  if (topics.includes("reporting")) {
    return "Review the official source and confirm any new reporting or documentation step with your DSO.";
  }
  if (finality === "proposal") {
    return "Treat this as an early warning and watch for a final rule or updated agency guidance before acting on it.";
  }
  return "Review the official source and update your OPT planning if the change affects your status, filings, or deadlines.";
}

function inferCallToActionRoute(text: string): string | null {
  const normalized = text.toLowerCase();
  if (/(travel|visa|reentry|interview waiver)/i.test(normalized)) return "travelAdvisor";
  if (/(reporting|sevis|portal|i-983|dso)/i.test(normalized)) return "reporting";
  if (/(uscis|i-765|application|filing)/i.test(normalized)) return "caseStatus";
  return null;
}

function inferCallToActionLabel(text: string): string | null {
  const route = inferCallToActionRoute(text);
  if (route === "travelAdvisor") return "Open Travel Advisor";
  if (route === "reporting") return "Open Reporting";
  if (route === "caseStatus") return "Open Case Status";
  return null;
}

function affectedOptTypesForAudience(audience: PolicyAlertAudience): string[] {
  switch (audience) {
  case "initial_opt":
    return ["initial"];
  case "stem_opt":
    return ["stem"];
  default:
    return ["initial", "stem"];
  }
}

function isLikelyPolicyAlertRelevant(signal: PolicyAlertSourceSignal): boolean {
  const normalized = `${signal.title} ${signal.summary} ${signal.bodyText}`.toLowerCase();
  return RELEVANCE_PATTERNS.some((pattern) => pattern.test(normalized));
}

function dedupeSignals(signals: PolicyAlertSourceSignal[]): PolicyAlertSourceSignal[] {
  const seen = new Set<string>();
  return signals.filter((signal) => {
    const key = `${signal.sourceUrl}|${signal.title}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function dedupeCandidates(candidates: PolicyAlertCandidateRecord[]): PolicyAlertCandidateRecord[] {
  const seen = new Map<string, PolicyAlertCandidateRecord>();
  for (const candidate of candidates) {
    seen.set(candidate.id, candidate);
  }
  return [...seen.values()];
}

function findLikelyDate(value: string): string | null {
  const match = value.match(/([A-Z][a-z]+ \d{1,2}, \d{4})/);
  return match ? match[1] : null;
}

function buildSeedPolicyAlertCandidates(now: number): PolicyAlertCandidateRecord[] {
  const seeds = [
    seedCandidate(
      "dhs-fixed-time-admission-proposed-rule",
      now,
      {
        title: "DHS proposed replacing duration of status with fixed admission periods for F, J, and I classifications",
        whatChanged: "DHS published a proposed rule on August 28, 2025 that would replace duration of status with fixed admission periods and new extension procedures for student and exchange visitor classifications.",
        effectiveDateText: "Proposal published August 28, 2025",
        whoIsAffected: "F-1 students on OPT or STEM OPT, plus prospective student-status applicants and schools monitoring SEVIS compliance changes.",
        whyItMatters: "If finalized, this would materially change how lawful student stay is measured and could affect OPT compliance timelines and extension workflows.",
        recommendedAction: "Review the proposal details with your DSO if you rely on future OPT or STEM timelines, and watch for any final-rule publication before changing travel or filing plans.",
        source: {
          label: "Federal Register Public Inspection",
          url: "https://public-inspection.federalregister.gov/2025-16554.pdf",
          publishedAt: Date.UTC(2025, 7, 28),
        },
        severity: "critical",
        confidence: "low",
        finality: "proposal",
        topics: ["applications", "reporting"],
        audience: "all_opt_users",
        affectedOptTypes: ["initial", "stem"],
        sendPush: false,
        urgentPush: false,
        documentNumber: "2025-16554",
        callToActionRoute: "reporting",
        callToActionLabel: "Open Reporting",
      }
    ),
    seedCandidate(
      "dos-country-of-residence-guidance",
      now,
      {
        title: "DOS says nonimmigrant visa applicants should interview in their country of nationality or residence",
        whatChanged: "DOS updated visa guidance on December 12, 2025 to say nonimmigrant visa applicants generally should interview in their country of nationality or residence.",
        effectiveDate: Date.UTC(2025, 11, 12),
        whoIsAffected: "OPT and STEM OPT students who may need a new F-1 visa while traveling abroad.",
        whyItMatters: "This changes the risk profile for third-country visa renewal strategies and directly affects travel planning for students outside their home country or residence.",
        recommendedAction: "Before planning visa renewal abroad, confirm whether your interview location matches your country of nationality or residence and revisit Travel Advisor for the latest travel-specific checks.",
        source: {
          label: "DOS U.S. Visas News",
          url: "https://travel.state.gov/content/travel/en/News/visas-news/adjudicating-niv-applicants-in-their-country-of-residence.html",
          publishedAt: Date.UTC(2025, 11, 12),
        },
        severity: "high",
        confidence: "high",
        finality: "guidance",
        topics: ["travel"],
        audience: "all_opt_users",
        affectedOptTypes: ["initial", "stem"],
        sendPush: true,
        urgentPush: false,
        callToActionRoute: "travelAdvisor",
        callToActionLabel: "Open Travel Advisor",
      }
    ),
    seedCandidate(
      "dos-proclamation-10998-suspension-guidance",
      now,
      {
        title: "DOS published visa-issuance suspension guidance tied to Proclamation 10998",
        whatChanged: "DOS published implementation guidance for Proclamation 10998 explaining visa-issuance suspensions and restrictions that affect certain foreign nationals, including F, M, and J visa paths in specific cases.",
        effectiveDate: Date.UTC(2026, 0, 1),
        whoIsAffected: "Students from restricted nationalities who would need a new F-1 visa to return to the United States.",
        whyItMatters: "This can turn a routine visa-renewal or reentry plan into a travel blocker, even if the student has valid OPT documents otherwise.",
        recommendedAction: "If your nationality may be affected and you need a new visa, treat the trip as high-risk and review the official DOS guidance before leaving the United States.",
        source: {
          label: "DOS U.S. Visas News",
          url: "https://travel.state.gov/content/travel/en/News/visas-news/suspension-of-visa-issuance-to-foreign-nationals-to-protect-the-security-of-the-united-states.html",
          publishedAt: Date.UTC(2026, 0, 1),
        },
        severity: "critical",
        confidence: "high",
        finality: "executive_action",
        topics: ["travel"],
        audience: "all_opt_users",
        affectedOptTypes: ["initial", "stem"],
        sendPush: true,
        urgentPush: true,
        documentNumber: "PP-10998",
        callToActionRoute: "travelAdvisor",
        callToActionLabel: "Open Travel Advisor",
      }
    ),
    seedCandidate(
      "dos-interview-waiver-update-2025",
      now,
      {
        title: "DOS narrowed interview-waiver eligibility effective September 2, 2025",
        whatChanged: "DOS announced on July 25, 2025 that interview-waiver eligibility would narrow effective September 2, 2025, changing how quickly some students can renew visas abroad.",
        effectiveDate: Date.UTC(2025, 8, 2),
        whoIsAffected: "OPT and STEM OPT students considering a visa renewal outside the United States.",
        whyItMatters: "Students who previously expected interview-waiver processing may now need a full interview appointment, which can extend travel risk and consular wait times.",
        recommendedAction: "If you are planning visa renewal abroad, check the current DOS interview-waiver rules and wait times before finalizing travel.",
        source: {
          label: "DOS U.S. Visas News",
          url: "https://travel.state.gov/content/travel/en/News/visas-news/interview-waiver-update-july-25-2025.html",
          publishedAt: Date.UTC(2025, 6, 25),
        },
        severity: "high",
        confidence: "high",
        finality: "operations",
        topics: ["travel", "applications"],
        audience: "all_opt_users",
        affectedOptTypes: ["initial", "stem"],
        sendPush: true,
        urgentPush: false,
        callToActionRoute: "travelAdvisor",
        callToActionLabel: "Open Travel Advisor",
      }
    ),
  ];
  return seeds;
}

function seedCandidate(
  idSeed: string,
  now: number,
  seed: Partial<PolicyAlertCandidateRecord> & {
    title: string;
    whatChanged: string;
    whoIsAffected: string;
    whyItMatters: string;
    recommendedAction: string;
    source: PolicyAlertSourceRecord;
    severity: PolicyAlertSeverity;
    confidence: PolicyAlertConfidence;
    finality: PolicyAlertFinality;
    topics: PolicyAlertTopic[];
    audience: PolicyAlertAudience;
    affectedOptTypes: string[];
    sendPush: boolean;
    urgentPush: boolean;
  }
): PolicyAlertCandidateRecord {
  const sourceHash = createHash("sha256")
    .update(`${seed.source.url}|${seed.title}|${seed.whatChanged}`)
    .digest("hex");
  const dedupeKey = createHash("sha256")
    .update(`${idSeed}|${seed.source.url}|${sourceHash}`)
    .digest("hex");
  return {
    id: dedupeKey,
    status: "needs_review",
    effectiveDate: null,
    effectiveDateText: null,
    lastReviewedAt: null,
    supersedesAlertId: null,
    supersededByAlertId: null,
    isArchived: false,
    isSuperseded: false,
    dedupeKey,
    sourceHash,
    sourceDomain: hostForUrl(seed.source.url),
    documentNumber: null,
    firstDetectedAt: now,
    lastDetectedAt: now,
    publishedAt: null,
    publishedBy: "",
    reviewedBy: "",
    callToActionRoute: null,
    callToActionLabel: null,
    rawSourceSnapshot: {
      kind: "seed",
      researchDate: "2026-03-10",
    },
    publishError: "",
    ...seed,
  };
}

async function upsertPolicyAlertCandidate(candidate: PolicyAlertCandidateRecord): Promise<void> {
  const ref = policyAlertCandidateDocRef(candidate.id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    await ref.set(candidate);
    return;
  }

  const rawCurrent = toRecord(snapshot.data());
  const current = toPolicyAlertCandidateRecord(candidate.id, snapshot.data());
  await ref.set(
    {
      title: current.title || candidate.title,
      whatChanged: current.whatChanged || candidate.whatChanged,
      effectiveDate: current.effectiveDate || candidate.effectiveDate,
      effectiveDateText: current.effectiveDateText || candidate.effectiveDateText,
      whoIsAffected: current.whoIsAffected || candidate.whoIsAffected,
      whyItMatters: current.whyItMatters || candidate.whyItMatters,
      recommendedAction: current.recommendedAction || candidate.recommendedAction,
      source: current.source.url ? current.source : candidate.source,
      severity: current.severity || candidate.severity,
      confidence: current.confidence || candidate.confidence,
      finality: current.finality || candidate.finality,
      topics: current.topics.length > 0 ? current.topics : candidate.topics,
      audience: current.audience || candidate.audience,
      affectedOptTypes: current.affectedOptTypes.length > 0 ? current.affectedOptTypes : candidate.affectedOptTypes,
      callToActionRoute: current.callToActionRoute || candidate.callToActionRoute,
      callToActionLabel: current.callToActionLabel || candidate.callToActionLabel,
      documentNumber: current.documentNumber || candidate.documentNumber,
      sendPush: typeof rawCurrent.sendPush === "boolean" ? rawCurrent.sendPush : candidate.sendPush,
      urgentPush: typeof rawCurrent.urgentPush === "boolean" ? rawCurrent.urgentPush : candidate.urgentPush,
      dedupeKey: candidate.dedupeKey,
      sourceHash: candidate.sourceHash,
      sourceDomain: candidate.sourceDomain,
      rawSourceSnapshot: candidate.rawSourceSnapshot,
      firstDetectedAt: current.firstDetectedAt || candidate.firstDetectedAt,
      lastDetectedAt: candidate.lastDetectedAt,
    },
    { merge: true }
  );
}

function validateCandidateForPublish(candidate: PolicyAlertCandidateRecord): void {
  if (!isOfficialGovernmentSourceUrl(candidate.source.url)) {
    throw new Error("Source URL must be an official government domain.");
  }
  if (!candidate.whatChanged.trim()) throw new Error("whatChanged is required.");
  if (!candidate.whoIsAffected.trim()) throw new Error("whoIsAffected is required.");
  if (!candidate.recommendedAction.trim()) throw new Error("recommendedAction is required.");
  if (candidate.effectiveDate == null && !candidate.effectiveDateText?.trim()) {
    throw new Error("Either effectiveDate or effectiveDateText is required.");
  }
  if (!POLICY_ALERT_SEVERITIES.includes(candidate.severity)) throw new Error("Invalid severity.");
  if (!POLICY_ALERT_CONFIDENCES.includes(candidate.confidence)) throw new Error("Invalid confidence.");
  if (!POLICY_ALERT_FINALITIES.includes(candidate.finality)) throw new Error("Invalid finality.");
}

function buildPublishedPolicyAlert(
  candidate: PolicyAlertCandidateRecord,
  publishedAt: number,
  publishedBy: string
): PolicyAlertPublishedRecord {
  return {
    id: candidate.id,
    title: candidate.title,
    whatChanged: candidate.whatChanged,
    effectiveDate: candidate.effectiveDate,
    effectiveDateText: candidate.effectiveDateText,
    whoIsAffected: candidate.whoIsAffected,
    whyItMatters: candidate.whyItMatters,
    recommendedAction: candidate.recommendedAction,
    source: candidate.source,
    lastReviewedAt: candidate.lastReviewedAt ?? publishedAt,
    severity: candidate.severity,
    confidence: candidate.confidence,
    finality: candidate.finality,
    topics: candidate.topics,
    audience: candidate.audience,
    affectedOptTypes: candidate.affectedOptTypes,
    supersedesAlertId: candidate.supersedesAlertId,
    supersededByAlertId: candidate.supersededByAlertId,
    isArchived: candidate.isArchived,
    isSuperseded: candidate.isSuperseded,
    sendPush: candidate.sendPush,
    urgentPush: candidate.urgentPush,
    dedupeKey: candidate.dedupeKey,
    sourceHash: candidate.sourceHash,
    sourceDomain: candidate.sourceDomain,
    documentNumber: candidate.documentNumber,
    candidateId: candidate.id,
    publishedAt,
    publishedBy,
    callToActionRoute: candidate.callToActionRoute,
    callToActionLabel: candidate.callToActionLabel,
    updatedAt: publishedAt,
  };
}

function shouldSendPolicyAlertPush(alert: PolicyAlertPublishedRecord): boolean {
  if (alert.isArchived || alert.isSuperseded) return false;
  if (alert.confidence === "low") return false;
  if (alert.severity === "critical") return true;
  if (alert.severity === "high" && alert.confidence === "high") return true;
  return alert.severity === "medium" && alert.urgentPush;
}

async function sendPolicyAlertNotifications(alert: PolicyAlertPublishedRecord): Promise<void> {
  const endpoints = await firestore
    .collectionGroup("notificationDevices")
    .where("policyAlertsEnabled", "==", true)
    .get();

  const profileCache = new Map<string, string | null>();
  for (const doc of endpoints.docs) {
    const endpoint = toNotificationDeviceRecord(doc.data());
    if (!endpoint?.token) {
      continue;
    }
    const userId = doc.ref.parent.parent?.id;
    if (!userId) {
      continue;
    }
    const optType = await resolveUserOptType(userId, profileCache);
    if (!alertMatchesOptType(alert.audience, optType)) {
      continue;
    }
    try {
      await admin.messaging().send({
        token: endpoint.token,
        data: {
          type: POLICY_ALERT_NOTIFICATION_TYPE,
          alertId: alert.id,
          title: "Policy alert",
          body: trimToLength(alert.title, 120),
        },
        android: {
          priority: "high",
        },
      });
    } catch (error) {
      const code = (error as { code?: string })?.code || "";
      logger.error("Failed to send policy alert notification", {
        alertId: alert.id,
        userId,
        installationId: endpoint.installationId,
        code,
      });
      if (code.includes("registration-token-not-registered") || code.includes("invalid-registration-token")) {
        await doc.ref.delete().catch(() => undefined);
      }
    }
  }
}

async function resolveUserOptType(
  userId: string,
  cache: Map<string, string | null>
): Promise<string | null> {
  if (cache.has(userId)) {
    return cache.get(userId) || null;
  }
  const snapshot = await firestore.collection("users").doc(userId).get();
  const optType = asOptionalString(snapshot.data()?.optType);
  cache.set(userId, optType);
  return optType;
}

async function upsertNotificationDevice(
  userId: string,
  installationId: string,
  token: string | null,
  caseStatusEnabled: boolean | null,
  policyAlertsEnabled: boolean | null
): Promise<{ enabled: boolean; caseStatusEnabled: boolean; policyAlertsEnabled: boolean }> {
  const ref = notificationDeviceDocRef(userId, installationId);
  const current = await ref.get();
  const existing = toNotificationDeviceRecord(current.data());
  const nextCaseStatusEnabled = caseStatusEnabled ?? existing?.caseStatusEnabled ?? false;
  const nextPolicyAlertsEnabled = policyAlertsEnabled ?? existing?.policyAlertsEnabled ?? false;
  const nextToken = token ?? existing?.token ?? null;

  if (!nextToken || (!nextCaseStatusEnabled && !nextPolicyAlertsEnabled)) {
    await ref.delete().catch(() => undefined);
    return {
      enabled: false,
      caseStatusEnabled: nextCaseStatusEnabled,
      policyAlertsEnabled: nextPolicyAlertsEnabled,
    };
  }

  const now = Date.now();
  const payload: NotificationDeviceRecord = {
    installationId,
    token: nextToken,
    platform: DEVICE_PLATFORM,
    enabled: true,
    caseStatusEnabled: nextCaseStatusEnabled,
    policyAlertsEnabled: nextPolicyAlertsEnabled,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };
  await ref.set(payload);
  return {
    enabled: true,
    caseStatusEnabled: nextCaseStatusEnabled,
    policyAlertsEnabled: nextPolicyAlertsEnabled,
  };
}

function isOfficialGovernmentSourceUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" && parsed.hostname.toLowerCase().endsWith(".gov");
  } catch {
    return false;
  }
}

function alertMatchesOptType(audience: PolicyAlertAudience, optType: string | null): boolean {
  switch (audience) {
  case "initial_opt":
    return (optType || "").toLowerCase() !== "stem";
  case "stem_opt":
    return (optType || "").toLowerCase() === "stem";
  default:
    return true;
  }
}

function toPolicyAlertCandidateRecord(id: string, value: unknown): PolicyAlertCandidateRecord {
  const record = toRecord(value);
  const source = toRecord(record.source);
  return {
    id,
    status: normalizeCandidateStatus(asOptionalString(record.status)),
    title: asOptionalString(record.title) || "",
    whatChanged: asOptionalString(record.whatChanged) || "",
    effectiveDate: parseDateValue(record.effectiveDate),
    effectiveDateText: asOptionalString(record.effectiveDateText),
    whoIsAffected: asOptionalString(record.whoIsAffected) || "",
    whyItMatters: asOptionalString(record.whyItMatters) || "",
    recommendedAction: asOptionalString(record.recommendedAction) || "",
    source: {
      label: asOptionalString(source.label) || "",
      url: asOptionalString(source.url) || "",
      publishedAt: parseDateValue(source.publishedAt),
    },
    lastReviewedAt: parseDateValue(record.lastReviewedAt),
    severity: normalizeSeverity(asOptionalString(record.severity)),
    confidence: normalizeConfidence(asOptionalString(record.confidence)),
    finality: normalizeFinality(asOptionalString(record.finality)),
    topics: normalizeTopics(record.topics),
    audience: normalizeAudience(asOptionalString(record.audience)),
    affectedOptTypes: toStringList(record.affectedOptTypes),
    supersedesAlertId: asOptionalString(record.supersedesAlertId),
    supersededByAlertId: asOptionalString(record.supersededByAlertId),
    isArchived: Boolean(record.isArchived),
    isSuperseded: Boolean(record.isSuperseded),
    sendPush: record.sendPush !== false,
    urgentPush: Boolean(record.urgentPush),
    dedupeKey: asOptionalString(record.dedupeKey) || id,
    sourceHash: asOptionalString(record.sourceHash) || "",
    sourceDomain: asOptionalString(record.sourceDomain) || "",
    documentNumber: asOptionalString(record.documentNumber),
    firstDetectedAt: existingNumber(record.firstDetectedAt) || 0,
    lastDetectedAt: existingNumber(record.lastDetectedAt) || 0,
    publishedAt: parseDateValue(record.publishedAt),
    publishedBy: asOptionalString(record.publishedBy) || "",
    reviewedBy: asOptionalString(record.reviewedBy) || "",
    callToActionRoute: asOptionalString(record.callToActionRoute),
    callToActionLabel: asOptionalString(record.callToActionLabel),
    rawSourceSnapshot: toRecord(record.rawSourceSnapshot),
    publishError: asOptionalString(record.publishError) || "",
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
    caseStatusEnabled: Boolean(record.caseStatusEnabled),
    policyAlertsEnabled: Boolean(record.policyAlertsEnabled),
    createdAt: existingNumber(record.createdAt) || 0,
    updatedAt: existingNumber(record.updatedAt) || 0,
  };
}

function normalizeCandidateStatus(value: string | null): PolicyAlertCandidateStatus {
  switch (value) {
  case "approved":
  case "dismissed":
  case "published":
    return value;
  default:
    return "needs_review";
  }
}

function normalizeSeverity(value: string | null): PolicyAlertSeverity {
  switch (value) {
  case "critical":
  case "high":
  case "medium":
    return value;
  default:
    return "low";
  }
}

function normalizeConfidence(value: string | null): PolicyAlertConfidence {
  switch (value) {
  case "high":
  case "medium":
    return value;
  default:
    return "low";
  }
}

function normalizeFinality(value: string | null): PolicyAlertFinality {
  switch (value) {
  case "final":
  case "guidance":
  case "operations":
  case "proposal":
  case "executive_action":
  case "litigation":
    return value;
  default:
    return "guidance";
  }
}

function normalizeTopics(value: unknown): PolicyAlertTopic[] {
  return toStringList(value)
    .map((item) => item.toLowerCase())
    .filter((item): item is PolicyAlertTopic => POLICY_ALERT_TOPICS.includes(item as PolicyAlertTopic));
}

function normalizeAudience(value: string | null): PolicyAlertAudience {
  switch (value) {
  case "all":
  case "initial_opt":
  case "stem_opt":
    return value;
  default:
    return "all_opt_users";
  }
}

export {
  buildPolicyAlertCandidateFromSignal,
  buildPublishedPolicyAlert,
  buildSeedPolicyAlertCandidates,
  extractFederalRegisterSignalsFromResponse,
  extractSignalsFromHtml,
  isLikelyPolicyAlertRelevant,
  isOfficialGovernmentSourceUrl,
  validateCandidateForPublish,
};
