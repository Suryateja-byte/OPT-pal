import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { firestore } from "./documentSecurity";

const DAY_MS = 24 * 60 * 60 * 1000;
const MIN_COHORT_SIZE = 30;
const PEER_DATA_VERSION = "2026.03.11";
const PEER_DATA_FACTS_COLLECTION = "peerDataFacts";
const PEER_DATA_SNAPSHOTS_COLLECTION = "peerDataSnapshots";
const PEER_DATA_META_COLLECTION = "peerDataMeta";
const PEER_DATA_CONFIG_DOC = "config";
const PEER_DATA_OFFICIAL_BASELINES_DOC = "officialBaselines";

type OptTrack = "initial_opt" | "stem_opt" | "h1b_transition";
type H1bTrack = "cap_subject" | "cap_exempt" | "unknown";
type H1bStageBucket = "planned" | "submitted" | "selected" | "filed" | "receipt";

type UserProfileDoc = {
  optType?: string;
  optStartDate?: number;
  cipCode?: string;
  majorName?: string;
};

type EmploymentDoc = {
  startDate?: number;
  hoursPerWeek?: number | null;
};

type ReportingWizardDoc = {
  dueDate?: number;
  completedAt?: number | null;
  optRegime?: string;
};

type H1bProfileDoc = {
  employerType?: string;
};

type H1bTimelineDoc = {
  workflowStage?: string;
  selectedRegistration?: boolean | null;
  filedPetition?: boolean | null;
  selectedAt?: number | null;
  petitionFiledAt?: number | null;
  receiptReceivedAt?: number | null;
};

type PeerDataSettingsDoc = {
  contributionEnabled?: boolean;
  contributionVersion?: string;
  previewedAt?: number | null;
  withdrawnAt?: number | null;
  updatedAt?: number;
};

type PeerFact = {
  uid: string;
  optTrack: OptTrack;
  cip2digitFamily: string | null;
  cip2digitLabel: string | null;
  optStartHalfYear: string | null;
  h1bTrack: H1bTrack | null;
  employmentTiming?: {
    employedBy30: boolean;
    employedBy60: boolean;
    employedBy90: boolean;
    firstJobBandStart: number;
  };
  reportingTimeliness?: {
    eligibleCount: number;
    onTimeCount: number;
  };
  stemContinuity?: {
    eligibleCount: number;
    onTimeCount: number;
  };
  h1bStage?: H1bStageBucket | null;
  h1bTiming?: {
    selectedToFiledBandStart?: number;
    filedToReceiptBandStart?: number;
  };
  derivedAt: number;
};

type StoredPeerFact = {
  uid: string;
  optTrack: OptTrack;
  cip2digitFamily: string | null;
  cip2digitLabel: string | null;
  optStartHalfYear: string | null;
  h1bTrack: H1bTrack | null;
  employmentTiming?: PeerFact["employmentTiming"];
  reportingTimeliness?: PeerFact["reportingTimeliness"];
  stemContinuity?: PeerFact["stemContinuity"];
  h1bStage?: H1bStageBucket | null;
  h1bTiming?: PeerFact["h1bTiming"];
  derivedAt: number;
  contributionVersion: string;
};

type GeneralAccumulator = {
  userCount: number;
  optTrack: OptTrack;
  cip2digitFamily: string | null;
  cip2digitLabel: string | null;
  optStartHalfYear: string | null;
  employmentEligible: number;
  employedBy30Count: number;
  employedBy60Count: number;
  employedBy90Count: number;
  firstJobBands: number[];
  reportingEligible: number;
  reportingOnTime: number;
  stemEligible: number;
  stemOnTime: number;
};

type H1bAccumulator = {
  userCount: number;
  optTrack: OptTrack;
  cip2digitFamily: string | null;
  cip2digitLabel: string | null;
  optStartHalfYear: string | null;
  h1bTrack: H1bTrack;
  stageEligible: number;
  stageCounts: Record<H1bStageBucket, number>;
  selectedToFiledBands: number[];
  filedToReceiptBands: number[];
};

type CohortSpec = {
  docId: string;
  optTrack: OptTrack;
  cip2digitFamily: string | null;
  cip2digitLabel: string | null;
  optStartHalfYear: string | null;
  h1bTrack: H1bTrack | null;
  cohortBasis: string;
};

type SnapshotCohortDoc = {
  cohortKey: string;
  cohortBasis: string;
  benchmarkCards: Array<Record<string, unknown>>;
};

type StoredOfficialBaselines = {
  updatedAt?: number;
  cards?: Array<Record<string, unknown>>;
};

export function buildPeerDataBundle(now: number = Date.now()) {
  return {
    version: PEER_DATA_VERSION,
    generatedAt: now,
    lastReviewedAt: Date.UTC(2026, 2, 11),
    staleAfterDays: 45,
    sources: [
      citation(
        "ice_sevis_annual",
        "ICE SEVP Annual Report Release",
        "https://www.ice.gov/news/releases/ice-releases-2024-sevp-annual-report",
        "2025-06-05",
        "Annual SEVP reporting cadence and national context for F-1 records."
      ),
      citation(
        "ice_sevis_numbers",
        "2024 SEVIS by the Numbers",
        "https://www.ice.gov/doclib/sevis/btn/25_0605_2024-sevis-btn.pdf",
        "2025-06-05",
        "National active-record context for F-1 and other SEVIS populations."
      ),
      citation(
        "uscis_h1b_hub",
        "USCIS H-1B Employer Data Hub",
        "https://www.uscis.gov/archive/h-1b-employer-data-hub-files",
        "2026-03-11",
        "Official employer-history data with important limitations about first decisions only."
      ),
      citation(
        "dol_oflc_performance",
        "DOL OFLC Performance Data",
        "https://www.dol.gov/agencies/eta/foreign-labor/performance",
        "2026-01-01",
        "Quarterly and annual context for labor certification and LCA processing."
      ),
      citation(
        "nces_cip_2020",
        "NCES CIP 2020 Introduction",
        "https://nces.ed.gov/ipeds/cipcode/Files/2020_CIP_Introduction.pdf",
        "2020-01-01",
        "CIP 2020 2-digit family structure used for coarse cohorting."
      ),
      citation(
        "hhs_deid",
        "HHS De-Identification Guidance",
        "https://www.hhs.gov/guidance/sites/default/files/hhs-guidance-documents/hhs_deid_guidance.pdf",
        "Current as published",
        "Safe-harbor style privacy guardrails for suppression and aggregation."
      ),
    ],
    benchmarkDefinitions: [
      benchmarkDefinition("employment_timing", "Employment timing", "Qualifying-employment timing bands and by-day coverage checkpoints."),
      benchmarkDefinition("reporting_timeliness", "Reporting timeliness", "On-time completion rate for reporting tasks with known due dates."),
      benchmarkDefinition("stem_continuity", "STEM continuity", "On-time STEM validation and related reporting completion rate."),
      benchmarkDefinition("h1b_stage_distribution", "H-1B stage distribution", "Planned, submitted, selected, filed, and receipt-stage mix among similar opted-in users."),
      benchmarkDefinition("h1b_timing", "H-1B timing", "Median band from selected-to-filed and filed-to-receipt when enough data exists."),
    ],
    methodologyNotes: [
      methodology(
        "cohorting",
        "Coarse cohorting only",
        "Peer Data uses OPT track, CIP 2-digit family, and an OPT start half-year window. Employer names, school names, and exact dates are excluded from published cohorts."
      ),
      methodology(
        "suppression",
        "Suppression threshold",
        "Cohort cards are published only when at least 30 similar opted-in users exist, and metric cards require enough complete inputs to stay privacy-safe."
      ),
      methodology(
        "freshness",
        "Freshness and cadence",
        "Official baselines have different update cadences: SEVIS context is annual, OFLC context is quarterly, and USCIS H-1B context is periodic."
      ),
      methodology(
        "withdrawal",
        "Contribution withdrawal",
        "Turning contribution off stops future derived facts from being included and deletes the user-scoped normalized fact on the next rebuild. Historical snapshots remain versioned and de-identified."
      ),
    ],
    freshnessSummary: "Official baselines are versioned and reviewed separately from app-cohort snapshots. Published cohort cards stay coarse, rounded, and suppression-aware.",
    changelog: [
      {
        id: "peer_data_initial_release",
        title: "Initial Peer Data release",
        summary: "Read-only, privacy-safe peer benchmarks launched with official baseline context and opt-in app cohorts.",
        effectiveDate: "2026-03-11",
        citationId: "hhs_deid",
      },
    ],
  };
}

export const getPeerDataBundle = onCall(async (request) => {
  requireAuth(request.auth);
  return buildPeerDataBundle();
});

export const savePeerDataParticipation = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const contributionEnabled = asBoolean(request.data.contributionEnabled, "contributionEnabled");
  const now = Date.now();
  const previewedAt = asOptionalNumber(request.data.previewedAt) ?? now;
  const settingsRef = firestore.collection("users")
    .doc(uid)
    .collection("peerData")
    .doc("settings");

  const nextSettings = {
    id: "settings",
    contributionEnabled,
    contributionVersion: PEER_DATA_VERSION,
    previewedAt,
    withdrawnAt: contributionEnabled ? null : now,
    updatedAt: now,
  };

  await settingsRef.set(nextSettings, {merge: true});
  if (!contributionEnabled) {
    await firestore.collection(PEER_DATA_FACTS_COLLECTION).doc(uid).delete().catch(() => undefined);
  }

  return nextSettings;
});

export const getPeerDataSnapshot = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const now = Date.now();
  const userRef = firestore.collection("users").doc(uid);
  const userSnapshot = await userRef.get();
  if (!userSnapshot.exists) {
    throw new HttpsError("not-found", "User profile not found.");
  }

  const peerFact = await derivePeerFact(uid, toRecord(userSnapshot.data()), now);
  const config = toRecord((await firestore.collection(PEER_DATA_META_COLLECTION).doc(PEER_DATA_CONFIG_DOC).get()).data());
  const snapshotId = asOptionalString(config.currentSnapshotId);
  const generatedAt = asOptionalNumber(config.lastPromotedAt) ?? now;
  const officialContextCards = await loadSnapshotOfficialCards(snapshotId, now);

  let cohortCards: Array<Record<string, unknown>> = [];
  if (snapshotId && peerFact != null) {
    const generalDoc = await findFirstCohortDoc(snapshotId, buildGeneralCohortSpecs(peerFact));
    const h1bDoc = await findFirstCohortDoc(snapshotId, buildH1bCohortSpecs(peerFact));
    cohortCards = [
      ...(generalDoc?.benchmarkCards ?? []),
      ...(h1bDoc?.benchmarkCards ?? []),
    ];
  }

  const caveats = buildSnapshotCaveats(peerFact, cohortCards.length === 0);
  return {
    snapshotId: snapshotId || `live-${now}`,
    generatedAt,
    cohortDescriptors: buildUserCohortDescriptors(peerFact),
    benchmarkCards: cohortCards,
    officialContextCards,
    caveats,
    notEnoughSimilarPeers: cohortCards.length === 0,
  };
});

export const refreshPeerDataOfficialBaselines = onSchedule(
  {
    schedule: "every 24 hours",
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Date.now();
    const cards = buildOfficialContextCards(now);
    await firestore.collection(PEER_DATA_META_COLLECTION)
      .doc(PEER_DATA_OFFICIAL_BASELINES_DOC)
      .set(
        {
          updatedAt: now,
          cards,
          version: PEER_DATA_VERSION,
        },
        {merge: true}
      );
    logger.info("Peer Data official baselines refreshed.", {cardCount: cards.length});
  }
);

export const rebuildPeerDataSnapshots = onSchedule(
  {
    schedule: "every 24 hours",
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Date.now();
    const usersSnapshot = await firestore.collection("users").get();
    const officialCards = await loadStoredOfficialBaselineCards(now);
    const generalAccumulators = new Map<string, GeneralAccumulator>();
    const h1bAccumulators = new Map<string, H1bAccumulator>();
    const factWrites: Array<(batch: admin.firestore.WriteBatch) => void> = [];

    for (const userDoc of usersSnapshot.docs) {
      const uid = userDoc.id;
      const settingsSnapshot = await userDoc.ref.collection("peerData").doc("settings").get();
      const settings = toRecord(settingsSnapshot.data()) as PeerDataSettingsDoc;
      if (settings.contributionEnabled !== true) {
        factWrites.push((batch) => batch.delete(firestore.collection(PEER_DATA_FACTS_COLLECTION).doc(uid)));
        continue;
      }

      const peerFact = await derivePeerFact(uid, toRecord(userDoc.data()), now);
      if (peerFact == null) {
        factWrites.push((batch) => batch.delete(firestore.collection(PEER_DATA_FACTS_COLLECTION).doc(uid)));
        continue;
      }

      factWrites.push((batch) => {
        batch.set(
          firestore.collection(PEER_DATA_FACTS_COLLECTION).doc(uid),
          sanitizePeerFact(peerFact),
          {merge: true}
        );
      });
      addPeerFactToAccumulators(peerFact, generalAccumulators, h1bAccumulators);
    }

    await commitBatchOperations(factWrites);

    const snapshotId = `snapshot-${now}`;
    const snapshotRef = firestore.collection(PEER_DATA_SNAPSHOTS_COLLECTION).doc(snapshotId);
    const snapshotWrites: Array<(batch: admin.firestore.WriteBatch) => void> = [
      (batch) => batch.set(snapshotRef, {
        snapshotId,
        generatedAt: now,
        version: PEER_DATA_VERSION,
      }),
    ];

    for (const card of officialCards) {
      snapshotWrites.push((batch) => {
        batch.set(snapshotRef.collection("official").doc(`${card.id}`), card);
      });
    }

    let publishedCohorts = 0;
    for (const [docId, accumulator] of generalAccumulators.entries()) {
      if (accumulator.userCount < MIN_COHORT_SIZE) {
        continue;
      }
      const benchmarkCards = buildGeneralBenchmarkCards(accumulator, now);
      if (benchmarkCards.length === 0) {
        continue;
      }
      publishedCohorts += 1;
      snapshotWrites.push((batch) => {
        batch.set(snapshotRef.collection("cohorts").doc(docId), {
          cohortKey: docId,
          cohortBasis: buildCohortBasis(
            accumulator.optTrack,
            accumulator.cip2digitLabel,
            accumulator.optStartHalfYear,
            null
          ),
          benchmarkCards,
        });
      });
    }

    for (const [docId, accumulator] of h1bAccumulators.entries()) {
      if (accumulator.userCount < MIN_COHORT_SIZE) {
        continue;
      }
      const benchmarkCards = buildH1bBenchmarkCards(accumulator, now);
      if (benchmarkCards.length === 0) {
        continue;
      }
      publishedCohorts += 1;
      snapshotWrites.push((batch) => {
        batch.set(snapshotRef.collection("cohorts").doc(docId), {
          cohortKey: docId,
          cohortBasis: buildCohortBasis(
            accumulator.optTrack,
            accumulator.cip2digitLabel,
            accumulator.optStartHalfYear,
            accumulator.h1bTrack
          ),
          benchmarkCards,
        });
      });
    }

    await commitBatchOperations(snapshotWrites);
    await firestore.collection(PEER_DATA_META_COLLECTION).doc(PEER_DATA_CONFIG_DOC).set(
      {
        currentSnapshotId: snapshotId,
        lastPromotedAt: now,
        updatedAt: now,
        cohortCount: publishedCohorts,
        officialCardCount: officialCards.length,
      },
      {merge: true}
    );

    logger.info("Peer Data snapshot rebuilt.", {
      snapshotId,
      publishedCohorts,
      officialCardCount: officialCards.length,
    });
  }
);

export function deriveCip2DigitFamily(cipCode: string | null | undefined): string | null {
  const normalized = `${cipCode || ""}`.trim();
  const match = normalized.match(/^(\d{2})/);
  return match ? match[1] : null;
}

export function deriveOptStartHalfYear(optStartDate: number | null | undefined): string | null {
  if (!Number.isFinite(optStartDate)) {
    return null;
  }
  const date = new Date(Number(optStartDate));
  const year = date.getUTCFullYear();
  const half = date.getUTCMonth() < 6 ? "H1" : "H2";
  return `${year}-${half}`;
}

export function sampleSizeBand(count: number): string | null {
  if (count >= 100) return "100+";
  if (count >= 50) return "50-99";
  if (count >= 30) return "30-49";
  return null;
}

export function buildGeneralCohortDocIds(fact: Pick<PeerFact, "optTrack" | "cip2digitFamily" | "optStartHalfYear">): string[] {
  return buildGeneralCohortSpecs({
    optTrack: fact.optTrack,
    cip2digitFamily: fact.cip2digitFamily,
    cip2digitLabel: fact.cip2digitFamily ? cipFamilyLabel(fact.cip2digitFamily) : null,
    optStartHalfYear: fact.optStartHalfYear,
  }).map((item) => item.docId);
}

export function buildH1bCohortDocIds(fact: Pick<PeerFact, "optTrack" | "cip2digitFamily" | "optStartHalfYear" | "h1bTrack">): string[] {
  return buildH1bCohortSpecs({
    optTrack: fact.optTrack,
    cip2digitFamily: fact.cip2digitFamily,
    cip2digitLabel: fact.cip2digitFamily ? cipFamilyLabel(fact.cip2digitFamily) : null,
    optStartHalfYear: fact.optStartHalfYear,
    h1bTrack: fact.h1bTrack,
  }).map((item) => item.docId);
}

function benchmarkDefinition(id: string, title: string, summary: string) {
  return {id, title, summary};
}

function methodology(id: string, title: string, body: string) {
  return {id, title, body};
}

function citation(
  id: string,
  label: string,
  url: string,
  effectiveDate: string,
  summary: string
) {
  return {
    id,
    label,
    url,
    effectiveDate,
    lastReviewedDate: "2026-03-11",
    summary,
  };
}

function requireAuth(auth: { uid: string } | undefined): string {
  if (!auth?.uid) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  return auth.uid;
}

function asBoolean(value: unknown, fieldName: string): boolean {
  if (typeof value !== "boolean") {
    throw new HttpsError("invalid-argument", `${fieldName} must be a boolean.`);
  }
  return value;
}

function asOptionalNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function asOptionalString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed || null;
}

function toRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function sanitizePeerFact(fact: PeerFact): StoredPeerFact {
  return {
    uid: fact.uid,
    optTrack: fact.optTrack,
    cip2digitFamily: fact.cip2digitFamily,
    cip2digitLabel: fact.cip2digitLabel,
    optStartHalfYear: fact.optStartHalfYear,
    h1bTrack: fact.h1bTrack,
    employmentTiming: fact.employmentTiming,
    reportingTimeliness: fact.reportingTimeliness,
    stemContinuity: fact.stemContinuity,
    h1bStage: fact.h1bStage,
    h1bTiming: fact.h1bTiming,
    derivedAt: fact.derivedAt,
    contributionVersion: PEER_DATA_VERSION,
  };
}

async function derivePeerFact(
  uid: string,
  rawProfile: Record<string, unknown>,
  now: number
): Promise<PeerFact | null> {
  const profile = rawProfile as UserProfileDoc;
  const userRef = firestore.collection("users").doc(uid);
  const [
    employmentSnapshot,
    reportingWizardSnapshot,
    h1bProfileSnapshot,
    h1bTimelineSnapshot,
  ] = await Promise.all([
    userRef.collection("employment").get(),
    userRef.collection("reportingWizards").get(),
    userRef.collection("h1bDashboard").doc("profile").get(),
    userRef.collection("h1bDashboard").doc("timelineState").get(),
  ]);

  const employments = employmentSnapshot.docs.map((doc) => toRecord(doc.data()) as EmploymentDoc);
  const reportingWizards = reportingWizardSnapshot.docs.map((doc) => toRecord(doc.data()) as ReportingWizardDoc);
  const h1bProfile = toRecord(h1bProfileSnapshot.data()) as H1bProfileDoc;
  const h1bTimeline = toRecord(h1bTimelineSnapshot.data()) as H1bTimelineDoc;
  const optTrack = deriveOptTrack(profile.optType, h1bTimeline);
  if (optTrack == null) {
    return null;
  }

  const cip2digitFamily = deriveCip2DigitFamily(profile.cipCode);
  const optStartHalfYear = deriveOptStartHalfYear(profile.optStartDate);
  const h1bTrack = deriveH1bTrack(h1bProfile.employerType);

  return {
    uid,
    optTrack,
    cip2digitFamily,
    cip2digitLabel: cip2digitFamily ? cipFamilyLabel(cip2digitFamily) : null,
    optStartHalfYear,
    h1bTrack,
    employmentTiming: deriveEmploymentTiming(profile.optStartDate, employments),
    reportingTimeliness: deriveReportingMetric(reportingWizards, now, false),
    stemContinuity: deriveReportingMetric(reportingWizards, now, true),
    h1bStage: deriveH1bStage(h1bTimeline),
    h1bTiming: deriveH1bTiming(h1bTimeline),
    derivedAt: now,
  };
}

function deriveOptTrack(optType: string | undefined, h1bTimeline: H1bTimelineDoc): OptTrack | null {
  const normalizedOptType = `${optType || ""}`.trim().toLowerCase();
  if (hasActiveH1bTimeline(h1bTimeline)) {
    return "h1b_transition";
  }
  if (normalizedOptType === "stem") {
    return "stem_opt";
  }
  if (normalizedOptType === "initial" || normalizedOptType === "") {
    return "initial_opt";
  }
  return null;
}

function hasActiveH1bTimeline(h1bTimeline: H1bTimelineDoc): boolean {
  const stage = `${h1bTimeline.workflowStage || ""}`.trim();
  return [
    "registration_planned",
    "registration_submitted",
    "selected",
    "petition_filed",
    "receipt_received",
    "approved",
    "denied",
    "withdrawn",
  ].includes(stage) ||
    h1bTimeline.selectedRegistration === true ||
    h1bTimeline.filedPetition === true;
}

function deriveH1bTrack(employerType: string | undefined): H1bTrack | null {
  switch (`${employerType || ""}`.trim()) {
  case "university":
  case "nonprofit_research":
  case "government_research":
    return "cap_exempt";
  case "private_company":
  case "hospital":
  case "agent":
    return "cap_subject";
  default:
    return null;
  }
}

function deriveEmploymentTiming(
  optStartDate: number | undefined,
  employments: EmploymentDoc[]
): PeerFact["employmentTiming"] | undefined {
  if (!Number.isFinite(optStartDate)) {
    return undefined;
  }
  const qualifyingStartDate = employments
    .filter((employment) => (employment.hoursPerWeek ?? 0) >= 20 && Number.isFinite(employment.startDate))
    .map((employment) => Number(employment.startDate))
    .sort((left, right) => left - right)[0];

  if (!Number.isFinite(qualifyingStartDate)) {
    return undefined;
  }

  const daysToFirstJob = Math.max(0, Math.floor((qualifyingStartDate - Number(optStartDate)) / DAY_MS));
  return {
    employedBy30: daysToFirstJob <= 30,
    employedBy60: daysToFirstJob <= 60,
    employedBy90: daysToFirstJob <= 90,
    firstJobBandStart: bucketToFive(daysToFirstJob),
  };
}

function deriveReportingMetric(
  wizards: ReportingWizardDoc[],
  now: number,
  stemOnly: boolean
): PeerFact["reportingTimeliness"] | PeerFact["stemContinuity"] | undefined {
  const eligibleWizards = wizards.filter((wizard) => {
    if (!Number.isFinite(wizard.dueDate)) {
      return false;
    }
    if (stemOnly && `${wizard.optRegime || ""}`.trim() !== "stem") {
      return false;
    }
    if (!stemOnly && `${wizard.optRegime || ""}`.trim() === "stem") {
      return false;
    }
    return Number(wizard.dueDate) <= now || Number.isFinite(wizard.completedAt);
  });

  if (eligibleWizards.length === 0) {
    return undefined;
  }

  const onTimeCount = eligibleWizards.filter((wizard) =>
    Number.isFinite(wizard.completedAt) && Number(wizard.completedAt) <= Number(wizard.dueDate)
  ).length;

  return {
    eligibleCount: eligibleWizards.length,
    onTimeCount,
  };
}

function deriveH1bStage(h1bTimeline: H1bTimelineDoc): H1bStageBucket | null {
  switch (`${h1bTimeline.workflowStage || ""}`.trim()) {
  case "registration_planned":
    return "planned";
  case "registration_submitted":
    return "submitted";
  case "selected":
    return "selected";
  case "petition_filed":
    return "filed";
  case "receipt_received":
  case "approved":
  case "denied":
  case "withdrawn":
    return "receipt";
  default:
    return null;
  }
}

function deriveH1bTiming(h1bTimeline: H1bTimelineDoc): PeerFact["h1bTiming"] | undefined {
  const selectedAt = asOptionalNumber(h1bTimeline.selectedAt);
  const petitionFiledAt = asOptionalNumber(h1bTimeline.petitionFiledAt);
  const receiptReceivedAt = asOptionalNumber(h1bTimeline.receiptReceivedAt);
  const selectedToFiledDays = selectedAt != null && petitionFiledAt != null && petitionFiledAt >= selectedAt ?
    Math.floor((petitionFiledAt - selectedAt) / DAY_MS) :
    null;
  const filedToReceiptDays = petitionFiledAt != null && receiptReceivedAt != null && receiptReceivedAt >= petitionFiledAt ?
    Math.floor((receiptReceivedAt - petitionFiledAt) / DAY_MS) :
    null;

  if (selectedToFiledDays == null && filedToReceiptDays == null) {
    return undefined;
  }

  return {
    selectedToFiledBandStart: selectedToFiledDays == null ? undefined : bucketToFive(selectedToFiledDays),
    filedToReceiptBandStart: filedToReceiptDays == null ? undefined : bucketToFive(filedToReceiptDays),
  };
}

function addPeerFactToAccumulators(
  fact: PeerFact,
  generalAccumulators: Map<string, GeneralAccumulator>,
  h1bAccumulators: Map<string, H1bAccumulator>
) {
  for (const spec of buildGeneralCohortSpecs(fact)) {
    const accumulator = generalAccumulators.get(spec.docId) || createGeneralAccumulator(spec);
    accumulator.userCount += 1;
    if (fact.employmentTiming) {
      accumulator.employmentEligible += 1;
      accumulator.employedBy30Count += fact.employmentTiming.employedBy30 ? 1 : 0;
      accumulator.employedBy60Count += fact.employmentTiming.employedBy60 ? 1 : 0;
      accumulator.employedBy90Count += fact.employmentTiming.employedBy90 ? 1 : 0;
      accumulator.firstJobBands.push(fact.employmentTiming.firstJobBandStart);
    }
    if (fact.reportingTimeliness) {
      accumulator.reportingEligible += fact.reportingTimeliness.eligibleCount;
      accumulator.reportingOnTime += fact.reportingTimeliness.onTimeCount;
    }
    if (fact.stemContinuity) {
      accumulator.stemEligible += fact.stemContinuity.eligibleCount;
      accumulator.stemOnTime += fact.stemContinuity.onTimeCount;
    }
    generalAccumulators.set(spec.docId, accumulator);
  }

  for (const spec of buildH1bCohortSpecs(fact)) {
    const accumulator = h1bAccumulators.get(spec.docId) || createH1bAccumulator(spec);
    accumulator.userCount += 1;
    if (fact.h1bStage) {
      accumulator.stageEligible += 1;
      accumulator.stageCounts[fact.h1bStage] += 1;
    }
    if (fact.h1bTiming?.selectedToFiledBandStart != null) {
      accumulator.selectedToFiledBands.push(fact.h1bTiming.selectedToFiledBandStart);
    }
    if (fact.h1bTiming?.filedToReceiptBandStart != null) {
      accumulator.filedToReceiptBands.push(fact.h1bTiming.filedToReceiptBandStart);
    }
    h1bAccumulators.set(spec.docId, accumulator);
  }
}

function buildGeneralCohortSpecs(
  fact: Pick<PeerFact, "optTrack" | "cip2digitFamily" | "cip2digitLabel" | "optStartHalfYear">
): CohortSpec[] {
  const specs: CohortSpec[] = [];
  if (fact.cip2digitFamily && fact.optStartHalfYear) {
    specs.push(buildCohortSpec(fact.optTrack, fact.cip2digitFamily, fact.cip2digitLabel, fact.optStartHalfYear, null));
  }
  if (fact.cip2digitFamily) {
    specs.push(buildCohortSpec(fact.optTrack, fact.cip2digitFamily, fact.cip2digitLabel, null, null));
  }
  specs.push(buildCohortSpec(fact.optTrack, null, null, null, null));
  return specs;
}

function buildH1bCohortSpecs(
  fact: Pick<PeerFact, "optTrack" | "cip2digitFamily" | "cip2digitLabel" | "optStartHalfYear" | "h1bTrack">
): CohortSpec[] {
  if (!fact.h1bTrack) {
    return [];
  }
  const specs: CohortSpec[] = [];
  if (fact.cip2digitFamily && fact.optStartHalfYear) {
    specs.push(buildCohortSpec(fact.optTrack, fact.cip2digitFamily, fact.cip2digitLabel, fact.optStartHalfYear, fact.h1bTrack));
  }
  if (fact.cip2digitFamily) {
    specs.push(buildCohortSpec(fact.optTrack, fact.cip2digitFamily, fact.cip2digitLabel, null, fact.h1bTrack));
  }
  specs.push(buildCohortSpec(fact.optTrack, null, null, null, fact.h1bTrack));
  return specs;
}

function buildCohortSpec(
  optTrack: OptTrack,
  cip2digitFamily: string | null,
  cip2digitLabel: string | null,
  optStartHalfYear: string | null,
  h1bTrack: H1bTrack | null
): CohortSpec {
  return {
    docId: [
      h1bTrack ? "h1b" : "general",
      optTrack,
      cip2digitFamily || "all_cip",
      optStartHalfYear || "all_dates",
      h1bTrack || "no_h1b_track",
    ].join("__"),
    optTrack,
    cip2digitFamily,
    cip2digitLabel,
    optStartHalfYear,
    h1bTrack,
    cohortBasis: buildCohortBasis(optTrack, cip2digitLabel, optStartHalfYear, h1bTrack),
  };
}

function createGeneralAccumulator(spec: CohortSpec): GeneralAccumulator {
  return {
    userCount: 0,
    optTrack: spec.optTrack,
    cip2digitFamily: spec.cip2digitFamily,
    cip2digitLabel: spec.cip2digitLabel,
    optStartHalfYear: spec.optStartHalfYear,
    employmentEligible: 0,
    employedBy30Count: 0,
    employedBy60Count: 0,
    employedBy90Count: 0,
    firstJobBands: [],
    reportingEligible: 0,
    reportingOnTime: 0,
    stemEligible: 0,
    stemOnTime: 0,
  };
}

function createH1bAccumulator(spec: CohortSpec): H1bAccumulator {
  return {
    userCount: 0,
    optTrack: spec.optTrack,
    cip2digitFamily: spec.cip2digitFamily,
    cip2digitLabel: spec.cip2digitLabel,
    optStartHalfYear: spec.optStartHalfYear,
    h1bTrack: spec.h1bTrack || "unknown",
    stageEligible: 0,
    stageCounts: {planned: 0, submitted: 0, selected: 0, filed: 0, receipt: 0},
    selectedToFiledBands: [],
    filedToReceiptBands: [],
  };
}

function buildGeneralBenchmarkCards(accumulator: GeneralAccumulator, now: number): Array<Record<string, unknown>> {
  const cards: Array<Record<string, unknown>> = [];
  const cohortBasis = buildCohortBasis(
    accumulator.optTrack,
    accumulator.cip2digitLabel,
    accumulator.optStartHalfYear,
    null
  );
  if (accumulator.employmentEligible >= MIN_COHORT_SIZE) {
    cards.push({
      id: "employment_timing",
      title: "Employment timing",
      summary: `About ${wholePercent(accumulator.employedBy30Count, accumulator.employmentEligible)}% were in qualifying work by day 30, ${wholePercent(accumulator.employedBy60Count, accumulator.employmentEligible)}% by day 60, and ${wholePercent(accumulator.employedBy90Count, accumulator.employmentEligible)}% by day 90. Median first-job timing was about ${bandLabel(medianBandStart(accumulator.firstJobBands))}.`,
      source: "app_cohort",
      cohortBasis,
      sampleSizeBand: sampleSizeBand(accumulator.employmentEligible),
      lastUpdatedAt: now,
      whatThisDoesNotMean: "This does not predict your own job-search timeline or reveal exact cohort counts.",
      citationIds: ["ice_sevis_numbers"],
    });
  }
  if (accumulator.reportingEligible >= MIN_COHORT_SIZE) {
    cards.push({
      id: "reporting_timeliness",
      title: "Reporting timeliness",
      summary: `About ${wholePercent(accumulator.reportingOnTime, accumulator.reportingEligible)}% completed comparable reporting on time when a due date was already known.`,
      source: "app_cohort",
      cohortBasis,
      sampleSizeBand: sampleSizeBand(accumulator.reportingEligible),
      lastUpdatedAt: now,
      whatThisDoesNotMean: "This does not excuse a missed deadline or confirm a manual reporting path was accepted.",
      citationIds: ["ice_sevis_annual"],
    });
  }
  if (accumulator.stemEligible >= MIN_COHORT_SIZE) {
    cards.push({
      id: "stem_continuity",
      title: "STEM continuity",
      summary: `Among comparable STEM reporting records, about ${wholePercent(accumulator.stemOnTime, accumulator.stemEligible)}% landed on time.`,
      source: "app_cohort",
      cohortBasis,
      sampleSizeBand: sampleSizeBand(accumulator.stemEligible),
      lastUpdatedAt: now,
      whatThisDoesNotMean: "This does not confirm a STEM employer change was fully compliant or that a missing form can be cured automatically.",
      citationIds: ["ice_sevis_annual"],
    });
  }
  return cards;
}

function buildH1bBenchmarkCards(accumulator: H1bAccumulator, now: number): Array<Record<string, unknown>> {
  const cards: Array<Record<string, unknown>> = [];
  const cohortBasis = buildCohortBasis(
    accumulator.optTrack,
    accumulator.cip2digitLabel,
    accumulator.optStartHalfYear,
    accumulator.h1bTrack
  );
  if (accumulator.stageEligible >= MIN_COHORT_SIZE) {
    cards.push({
      id: "h1b_stage_distribution",
      title: "H-1B stage distribution",
      summary: `Stage mix in this cohort is about ${wholePercent(accumulator.stageCounts.planned, accumulator.stageEligible)}% planned, ${wholePercent(accumulator.stageCounts.submitted, accumulator.stageEligible)}% submitted, ${wholePercent(accumulator.stageCounts.selected, accumulator.stageEligible)}% selected, ${wholePercent(accumulator.stageCounts.filed, accumulator.stageEligible)}% filed, and ${wholePercent(accumulator.stageCounts.receipt, accumulator.stageEligible)}% at receipt or beyond.`,
      source: "app_cohort",
      cohortBasis,
      sampleSizeBand: sampleSizeBand(accumulator.stageEligible),
      lastUpdatedAt: now,
      whatThisDoesNotMean: "This does not estimate your own selection or petition outcome.",
      citationIds: ["uscis_h1b_hub"],
    });
  }
  if (accumulator.selectedToFiledBands.length >= MIN_COHORT_SIZE || accumulator.filedToReceiptBands.length >= MIN_COHORT_SIZE) {
    const timingSummaryParts: string[] = [];
    if (accumulator.selectedToFiledBands.length >= MIN_COHORT_SIZE) {
      timingSummaryParts.push(`selected-to-filed timing clustered around ${bandLabel(medianBandStart(accumulator.selectedToFiledBands))}`);
    }
    if (accumulator.filedToReceiptBands.length >= MIN_COHORT_SIZE) {
      timingSummaryParts.push(`filed-to-receipt timing clustered around ${bandLabel(medianBandStart(accumulator.filedToReceiptBands))}`);
    }
    cards.push({
      id: "h1b_timing",
      title: "H-1B timing",
      summary: `Among similar opted-in users, ${timingSummaryParts.join(" and ")}.`,
      source: "app_cohort",
      cohortBasis,
      sampleSizeBand: sampleSizeBand(Math.max(accumulator.selectedToFiledBands.length, accumulator.filedToReceiptBands.length)),
      lastUpdatedAt: now,
      whatThisDoesNotMean: "This does not promise how fast a specific employer, attorney, or USCIS office will move.",
      citationIds: ["uscis_h1b_hub", "dol_oflc_performance"],
    });
  }
  return cards;
}

async function loadStoredOfficialBaselineCards(now: number): Promise<Array<Record<string, unknown>>> {
  const snapshot = await firestore.collection(PEER_DATA_META_COLLECTION).doc(PEER_DATA_OFFICIAL_BASELINES_DOC).get();
  const stored = toRecord(snapshot.data()) as StoredOfficialBaselines;
  if (Array.isArray(stored.cards) && stored.cards.length > 0) {
    return stored.cards;
  }
  return buildOfficialContextCards(now);
}

async function loadSnapshotOfficialCards(snapshotId: string | null, now: number): Promise<Array<Record<string, unknown>>> {
  if (!snapshotId) {
    return loadStoredOfficialBaselineCards(now);
  }
  const snapshot = await firestore.collection(PEER_DATA_SNAPSHOTS_COLLECTION)
    .doc(snapshotId)
    .collection("official")
    .get();
  if (!snapshot.empty) {
    return snapshot.docs
      .map((doc) => toRecord(doc.data()))
      .sort((left, right) => `${left.id || ""}`.localeCompare(`${right.id || ""}`));
  }
  return loadStoredOfficialBaselineCards(now);
}

function buildOfficialContextCards(now: number): Array<Record<string, unknown>> {
  return [
    {
      id: "official_f1_context",
      title: "Official F-1 context",
      summary: "Use national SEVIS context as a system-level backdrop, not a school-level or employer-level benchmark. Peer Data keeps the official baseline separate from app-cohort signals for exactly that reason.",
      source: "official",
      cohortBasis: "National SEVIS context",
      sampleSizeBand: null,
      lastUpdatedAt: Date.UTC(2025, 5, 5),
      whatThisDoesNotMean: "This does not describe your school, your major, or your local labor market.",
      citationIds: ["ice_sevis_annual", "ice_sevis_numbers"],
    },
    {
      id: "official_oflc_context",
      title: "Official labor-condition context",
      summary: "OFLC performance data is useful for national filing-volume and processing context, but it does not confirm whether a particular employer will sponsor or how quickly a specific case will move.",
      source: "official",
      cohortBasis: "National OFLC context",
      sampleSizeBand: null,
      lastUpdatedAt: Date.UTC(2026, 0, 1),
      whatThisDoesNotMean: "This does not reveal employer-specific sponsorship behavior.",
      citationIds: ["dol_oflc_performance"],
    },
    {
      id: "official_h1b_context",
      title: "Official H-1B employer-history context",
      summary: "USCIS employer-history data is useful for first-decision trend context, but it excludes pending cases, later appeals, and revocations. Peer Data keeps H-1B benchmarks descriptive only.",
      source: "official",
      cohortBasis: "National USCIS H-1B context",
      sampleSizeBand: null,
      lastUpdatedAt: now,
      whatThisDoesNotMean: "This does not predict selection, approval, or employer intent.",
      citationIds: ["uscis_h1b_hub"],
    },
  ];
}

async function findFirstCohortDoc(snapshotId: string, specs: CohortSpec[]): Promise<SnapshotCohortDoc | null> {
  for (const spec of specs) {
    const snapshot = await firestore.collection(PEER_DATA_SNAPSHOTS_COLLECTION)
      .doc(snapshotId)
      .collection("cohorts")
      .doc(spec.docId)
      .get();
    if (snapshot.exists) {
      const data = toRecord(snapshot.data());
      return {
        cohortKey: `${data.cohortKey || spec.docId}`,
        cohortBasis: `${data.cohortBasis || spec.cohortBasis}`,
        benchmarkCards: Array.isArray(data.benchmarkCards) ? data.benchmarkCards as Array<Record<string, unknown>> : [],
      };
    }
  }
  return null;
}

function buildSnapshotCaveats(peerFact: PeerFact | null, hasNoSimilarPeers: boolean): string[] {
  const caveats: string[] = [];
  if (hasNoSimilarPeers) {
    caveats.push("Not enough similar opt-in data is currently published for your cohort, so this view falls back to official context.");
  }
  if (peerFact == null) {
    caveats.push("Your current records are not complete enough to derive a personalized peer cohort yet.");
    return caveats;
  }
  if (!peerFact.cip2digitFamily) {
    caveats.push("Your CIP code is missing, so cohort matching falls back to a broader OPT track.");
  }
  if (!peerFact.optStartHalfYear) {
    caveats.push("Your OPT start date is missing, so cohort matching falls back to a broader OPT track.");
  }
  if (!peerFact.h1bTrack && peerFact.optTrack === "h1b_transition") {
    caveats.push("Your H-1B track is still too incomplete for cap-subject or cap-exempt cohort benchmarking.");
  }
  return caveats;
}

function buildUserCohortDescriptors(peerFact: PeerFact | null): Array<Record<string, unknown>> {
  if (peerFact == null) {
    return [
      {id: "opt_track", label: "OPT track", value: "Unavailable"},
      {id: "cip_family", label: "Major family", value: "Unavailable"},
      {id: "opt_start_halfyear", label: "Start window", value: "Unavailable"},
    ];
  }
  return [
    {id: "opt_track", label: "OPT track", value: optTrackLabel(peerFact.optTrack)},
    {id: "cip_family", label: "Major family", value: peerFact.cip2digitLabel || "Broader OPT cohort"},
    {id: "opt_start_halfyear", label: "Start window", value: peerFact.optStartHalfYear || "All dates"},
    {id: "h1b_track", label: "H-1B track", value: peerFact.h1bTrack ? h1bTrackLabel(peerFact.h1bTrack) : "Not applied"},
  ];
}

function buildCohortBasis(
  optTrack: OptTrack,
  cip2digitLabel: string | null,
  optStartHalfYear: string | null,
  h1bTrack: H1bTrack | null
): string {
  return [
    optTrackLabel(optTrack),
    cip2digitLabel,
    optStartHalfYear,
    h1bTrack ? h1bTrackLabel(h1bTrack) : null,
  ].filter((value): value is string => Boolean(value)).join(" • ");
}

function optTrackLabel(optTrack: OptTrack): string {
  switch (optTrack) {
  case "initial_opt":
    return "Initial OPT";
  case "stem_opt":
    return "STEM OPT";
  case "h1b_transition":
    return "H-1B transition";
  }
}

function h1bTrackLabel(h1bTrack: H1bTrack): string {
  switch (h1bTrack) {
  case "cap_exempt":
    return "Cap-exempt";
  case "cap_subject":
    return "Cap-subject";
  case "unknown":
    return "Unknown H-1B track";
  }
}

function cipFamilyLabel(cip2digitFamily: string): string {
  return CIP_2_DIGIT_LABELS[cip2digitFamily] || `CIP ${cip2digitFamily}`;
}

function wholePercent(numerator: number, denominator: number): number {
  if (denominator <= 0) {
    return 0;
  }
  return Math.round((numerator / denominator) * 100);
}

function bucketToFive(days: number): number {
  return Math.max(0, Math.floor(days / 5) * 5);
}

function bandLabel(bandStart: number): string {
  return `${bandStart}-${bandStart + 4} days`;
}

function medianBandStart(values: number[]): number {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.floor(sorted.length / 2)];
}

async function commitBatchOperations(
  operations: Array<(batch: admin.firestore.WriteBatch) => void>
): Promise<void> {
  if (operations.length === 0) {
    return;
  }
  let batch = firestore.batch();
  let writesInBatch = 0;
  for (const operation of operations) {
    operation(batch);
    writesInBatch += 1;
    if (writesInBatch === 400) {
      await batch.commit();
      batch = firestore.batch();
      writesInBatch = 0;
    }
  }
  if (writesInBatch > 0) {
    await batch.commit();
  }
}

const CIP_2_DIGIT_LABELS: Record<string, string> = {
  "01": "Agriculture and natural resources",
  "03": "Natural resources and conservation",
  "04": "Architecture and related services",
  "05": "Area, ethnic, cultural, gender, and group studies",
  "09": "Communication, journalism, and related programs",
  "10": "Communications technologies",
  "11": "Computer and information sciences",
  "12": "Personal and culinary services",
  "13": "Education",
  "14": "Engineering",
  "15": "Engineering technologies",
  "16": "Foreign languages and linguistics",
  "19": "Family and consumer sciences",
  "22": "Legal professions and studies",
  "23": "English language and literature",
  "24": "Liberal arts and sciences",
  "25": "Library science",
  "26": "Biological and biomedical sciences",
  "27": "Mathematics and statistics",
  "28": "Military science and leadership",
  "29": "Military technologies",
  "30": "Multidisciplinary and interdisciplinary studies",
  "31": "Parks, recreation, leisure, fitness, and kinesiology",
  "38": "Philosophy and religious studies",
  "39": "Theology and religious vocations",
  "40": "Physical sciences",
  "41": "Science technologies",
  "42": "Psychology",
  "43": "Homeland security, law enforcement, firefighting",
  "44": "Public administration and social service professions",
  "45": "Social sciences",
  "46": "Construction trades",
  "47": "Mechanic and repair technologies",
  "48": "Precision production",
  "49": "Transportation and materials moving",
  "50": "Visual and performing arts",
  "51": "Health professions and related programs",
  "52": "Business, management, marketing",
  "54": "History",
  "60": "Residency programs",
  "61": "Medical residency and fellowship programs",
};
