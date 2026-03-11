import * as logger from "firebase-functions/logger";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { firestore } from "./documentSecurity";

const H1B_EMPLOYER_COLLECTION = "h1bEmployerDataHub";
const H1B_EMPLOYER_ARCHIVE_URL = "https://www.uscis.gov/archive/h-1b-employer-data-hub-files";
const EVERIFY_SEARCH_URL = "https://www.e-verify.gov/e-verify-employer-search";

type EmployerHubRecord = {
  employerName: string;
  normalizedEmployerName: string;
  city: string;
  normalizedCity: string;
  state: string;
  taxIdLastFour: string;
  fiscalYear: number;
  sourceFile: string;
  lastIngestedAt: number;
  dataLimitations: string;
  searchTokens: string[];
  totalWorkers: number;
  initialApprovals: number;
  initialDenials: number;
  continuingApprovals: number;
  continuingDenials: number;
  changeOfEmployerApprovals: number;
  changeOfEmployerDenials: number;
};

export const getH1bDashboardBundle = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  return buildH1bDashboardBundle();
});

export const searchH1bEmployerHistory = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  const employerName = asNonEmptyString(request.data.employerName, "employerName");
  const employerCity = asOptionalString(request.data.employerCity) || "";
  const employerState = asOptionalString(request.data.employerState) || "";
  const normalizedName = normalizeLabel(employerName);
  const prefixSnapshot = await firestore.collection(H1B_EMPLOYER_COLLECTION)
    .orderBy("normalizedEmployerName")
    .startAt(normalizedName)
    .endAt(`${normalizedName}\uf8ff`)
    .limit(25)
    .get();

  const rawMatches = prefixSnapshot.docs
    .map((doc) => doc.data() as EmployerHubRecord)
    .filter((item) => {
      if (employerState && normalizeLabel(item.state) !== normalizeLabel(employerState)) {
        return false;
      }
      if (employerCity && normalizeLabel(item.city) !== normalizeLabel(employerCity)) {
        return false;
      }
      return true;
    });

  const matches = rawMatches.length > 0 ? rawMatches : prefixSnapshot.docs.map((doc) => doc.data() as EmployerHubRecord);
  if (matches.length === 0) {
    return {
      employerName,
      matchedEmployerName: "",
      city: employerCity,
      state: employerState,
      taxIdLastFour: "",
      sourceFile: "",
      lastIngestedAt: 0,
      dataLimitations: "No matching employer rows were found in the currently ingested USCIS dataset.",
      fiscalYearSummaries: [],
    };
  }

  const grouped = new Map<number, EmployerHubRecord[]>();
  for (const match of matches) {
    const current = grouped.get(match.fiscalYear) || [];
    current.push(match);
    grouped.set(match.fiscalYear, current);
  }
  const first = matches[0];
  return {
    employerName,
    matchedEmployerName: first.employerName,
    city: first.city,
    state: first.state,
    taxIdLastFour: first.taxIdLastFour,
    sourceFile: first.sourceFile,
    lastIngestedAt: Math.max(...matches.map((item) => item.lastIngestedAt)),
    dataLimitations: first.dataLimitations,
    fiscalYearSummaries: [...grouped.entries()]
      .sort(([left], [right]) => right - left)
      .map(([fiscalYear, items]) => ({
        fiscalYear,
        totalWorkers: sum(items, "totalWorkers"),
        initialApprovals: sum(items, "initialApprovals"),
        initialDenials: sum(items, "initialDenials"),
        continuingApprovals: sum(items, "continuingApprovals"),
        continuingDenials: sum(items, "continuingDenials"),
        changeOfEmployerApprovals: sum(items, "changeOfEmployerApprovals"),
        changeOfEmployerDenials: sum(items, "changeOfEmployerDenials"),
      })),
  };
});

export const saveEVerifySnapshot = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const employerName = asNonEmptyString(request.data.employerName, "employerName");
  const employerCity = asOptionalString(request.data.employerCity) || "";
  const employerState = asOptionalString(request.data.employerState) || "";
  const status = normalizeEVerifyStatus(asNonEmptyString(request.data.status, "status"));
  const ref = firestore.collection("users").doc(uid).collection("h1bDashboard").doc("employerVerification");
  const existingSnapshot = await ref.get();
  const existing = toRecord(existingSnapshot.data());
  const next = {
    id: "employerVerification",
    eVerifyStatus: status,
    eVerifyLookedUpAt: Date.now(),
    eVerifySourceUrl: EVERIFY_SEARCH_URL,
    eVerifyUserConfirmed: true,
    employerHistory: existing.employerHistory || {
      employerName,
      matchedEmployerName: employerName,
      city: employerCity,
      state: employerState,
      taxIdLastFour: "",
      sourceFile: "",
      lastIngestedAt: 0,
      dataLimitations: "",
      fiscalYearSummaries: [],
    },
    updatedAt: Date.now(),
  };
  await ref.set(next, { merge: true });
  return next;
});

export const syncH1bEmployerDataHub = onSchedule(
  {
    schedule: "every 24 hours",
    timeZone: "Etc/UTC",
  },
  async () => {
    try {
      await syncH1bEmployerDataHubData();
    } catch (error) {
      logger.error("H-1B employer data hub sync failed", {
        error: error instanceof Error ? error.message : `${error}`,
      });
    }
  }
);

export function buildH1bDashboardBundle() {
  const lastReviewedAt = Date.UTC(2026, 2, 11);
  return {
    version: "2026.03.11",
    generatedAt: Date.now(),
    lastReviewedAt,
    staleAfterDays: 30,
    citations: [
      citation(
        "uscis_registration",
        "USCIS H-1B Electronic Registration",
        "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/h-1b-electronic-registration-process",
        "2026-03-04",
        "Official registration timing, fees, and season updates."
      ),
      citation(
        "uscis_cap_season",
        "USCIS H-1B Cap Season",
        "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/h-1b-cap-season",
        "2026-02-27",
        "Cap limits, cap-exempt categories, and cap-subject filing timing."
      ),
      citation(
        "uscis_cap_gap",
        "USCIS Cap-Gap Guidance",
        "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/extension-of-post-completion-optional-practical-training-opt-and-f-1-status-for-eligible-students",
        "2025-01-17",
        "Cap-gap continuity rules and limits."
      ),
      citation(
        "uscis_employer_hub",
        "USCIS H-1B Employer Data Hub",
        "https://www.uscis.gov/tools/reports-and-studies/h-1b-employer-data-hub/understanding-our-h-1b-employer-data-hub",
        "2026-03-11",
        "Explains data fields, update cadence, and limitations for employer-history data."
      ),
      citation(
        "everify_search",
        "E-Verify Employer Search",
        EVERIFY_SEARCH_URL,
        "2026-03-11",
        "Public E-Verify search with daily updates and self-reported data caveats."
      ),
      citation(
        "weighted_selection_rule",
        "Weighted Selection Rule",
        "https://www.federalregister.gov/public-inspection/2025-23853/weighted-selection-process-for-registrants-and-petitioners-seeking-to-file-cap-subject-h-1b",
        "2026-02-27",
        "Federal Register record for the wage-weighted H-1B selection rule."
      ),
    ],
    changelog: [
      {
        id: "cap_gap_april_1",
        title: "Cap-gap end date is now April 1",
        summary: "The dashboard uses the current USCIS cap-gap rule and treats travel during sensitive cap-gap change-of-status scenarios as a hard stop.",
        effectiveDate: "2025-01-17",
        citationId: "uscis_cap_gap",
      },
      {
        id: "weighted_selection_live",
        title: "Weighted selection rule is active",
        summary: "The current season uses wage-level weighting instead of an unweighted lottery selection approach.",
        effectiveDate: "2026-02-27",
        citationId: "weighted_selection_rule",
      },
    ],
    ruleCards: [
      {
        id: "employer_verification",
        title: "Employer verification uses only documented signals",
        summary: "This dashboard uses E-Verify status, USCIS H-1B employer-history data, and employer-type classification. It does not score fraud or predict sponsorship quality.",
        confidence: "high",
        whatThisDoesNotMean: "A positive employer-history result does not guarantee future filings, and a missing result does not prove an employer will not sponsor.",
        citationIds: ["uscis_employer_hub", "everify_search", "uscis_cap_season"],
      },
      {
        id: "cap_gap_travel",
        title: "Cap-gap travel is a hard-stop state",
        summary: "The app escalates cap-gap travel scenarios instead of trying to auto-clear them.",
        confidence: "high",
        whatThisDoesNotMean: "This app does not replace DSO or attorney review for cap-gap travel or change-of-status risk.",
        citationIds: ["uscis_cap_gap"],
      },
      {
        id: "no_predictions",
        title: "No lottery or approval predictions",
        summary: "The dashboard stays deterministic and source-backed. It does not calculate approval odds or lottery chances.",
        confidence: "high",
        whatThisDoesNotMean: "A strong readiness state does not predict a selection or petition outcome.",
        citationIds: ["uscis_registration", "weighted_selection_rule"],
      },
    ],
    capSeason: {
      fiscalYear: 2027,
      registrationOpenAt: Date.UTC(2026, 2, 4, 17, 0, 0),
      registrationCloseAt: Date.UTC(2026, 2, 19, 16, 0, 0),
      petitionFilingOpensAt: Date.UTC(2026, 3, 1, 0, 0, 0),
      capGapEndAt: Date.UTC(2027, 3, 1, 0, 0, 0),
      weightedSelectionRuleEffectiveAt: Date.UTC(2026, 1, 27, 0, 0, 0),
      notes: "The FY 2027 registration period opens March 4, 2026 at noon ET and closes March 19, 2026 at noon ET. USCIS begins accepting selected cap-subject filings on April 1, 2026.",
    },
    eVerifySearchUrl: EVERIFY_SEARCH_URL,
    employerDataHubUrl: "https://www.uscis.gov/tools/reports-and-studies/h-1b-employer-data-hub/understanding-our-h-1b-employer-data-hub",
    capExemptCategories: [
      "U.S. institution of higher education",
      "Related or affiliated nonprofit entity",
      "Nonprofit research organization",
      "Governmental research organization",
    ],
  };
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
    lastReviewedAt: "2026-03-11",
    summary,
  };
}

function requireAuth(auth: { uid: string } | undefined): string {
  if (!auth?.uid) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  return auth.uid;
}

function normalizeEVerifyStatus(value: string): string {
  switch (value) {
  case "active":
  case "not_found":
  case "inactive":
    return value;
  default:
    throw new HttpsError("invalid-argument", "Unsupported E-Verify snapshot status.");
  }
}

function sum(items: EmployerHubRecord[], key: keyof EmployerHubRecord): number {
  return items.reduce((total, item) => total + (typeof item[key] === "number" ? Number(item[key]) : 0), 0);
}

function normalizeLabel(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
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

function toRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

async function syncH1bEmployerDataHubData(): Promise<void> {
  const sources = await resolveEmployerHubCsvUrls();
  if (sources.length === 0) {
    logger.warn("No H-1B employer data hub CSV sources discovered.");
    return;
  }
  for (const sourceUrl of sources.slice(0, 4)) {
    const response = await fetch(sourceUrl);
    if (!response.ok) {
      logger.warn("Skipping H-1B employer source because fetch failed.", {
        sourceUrl,
        status: response.status,
      });
      continue;
    }
    const csvText = await response.text();
    const records = parseEmployerHubCsv(csvText, sourceUrl);
    if (records.length === 0) {
      continue;
    }
    let batch = firestore.batch();
    let count = 0;
    for (const record of records) {
      batch.set(
        firestore.collection(H1B_EMPLOYER_COLLECTION).doc(buildEmployerDocId(record)),
        record,
        { merge: true }
      );
      count += 1;
      if (count % 400 === 0) {
        await batch.commit();
        batch = firestore.batch();
      }
    }
    if (count % 400 !== 0) {
      await batch.commit();
    }
    logger.info("Ingested H-1B employer data hub records.", {
      sourceUrl,
      count,
    });
  }
}

async function resolveEmployerHubCsvUrls(): Promise<string[]> {
  const configured = process.env.H1B_EMPLOYER_DATA_HUB_CSV_URLS?.split(",")
    .map((value) => value.trim())
    .filter(Boolean) || [];
  if (configured.length > 0) {
    return configured;
  }
  const response = await fetch(H1B_EMPLOYER_ARCHIVE_URL);
  if (!response.ok) {
    logger.warn("Unable to fetch H-1B employer archive page.", { status: response.status });
    return [];
  }
  const html = await response.text();
  const csvMatches = [...html.matchAll(/href="([^"]+\.csv[^"]*)"/gi)]
    .map((match) => match[1])
    .map((value) => value.startsWith("http") ? value : new URL(value, H1B_EMPLOYER_ARCHIVE_URL).toString());
  return [...new Set(csvMatches)];
}

function parseEmployerHubCsv(csvText: string, sourceUrl: string): EmployerHubRecord[] {
  const lines = csvText.split(/\r?\n/).filter((line) => line.trim().length > 0);
  if (lines.length < 2) {
    return [];
  }
  const header = splitCsvLine(lines[0]).map(normalizeLabel);
  const lastIngestedAt = Date.now();
  const sourceFile = sourceUrl.split("/").pop() || sourceUrl;
  return lines.slice(1).map((line) => {
    const values = splitCsvLine(line);
    const row = buildRowObject(header, values);
    return normalizeEmployerHubRecord(row, sourceFile, lastIngestedAt);
  }).filter((record): record is EmployerHubRecord => record != null);
}

function normalizeEmployerHubRecord(
  row: Record<string, string>,
  sourceFile: string,
  lastIngestedAt: number
): EmployerHubRecord | null {
  const employerName = firstValue(row,
    "employername",
    "petitionername",
    "companyname",
    "employer");
  const city = firstValue(row, "city", "petitionercity", "employercity");
  const state = firstValue(row, "state", "petitionerstate", "employerstate");
  const fiscalYear = Number(firstValue(row, "fiscalyear", "fy", "year"));
  if (!employerName || !Number.isFinite(fiscalYear)) {
    return null;
  }
  const taxIdLastFour = `${firstValue(row, "taxidlast4", "taxid", "taxidlastfour") || ""}`.slice(-4);
  return {
    employerName,
    normalizedEmployerName: normalizeLabel(employerName),
    city: city || "",
    normalizedCity: normalizeLabel(city || ""),
    state: state || "",
    taxIdLastFour,
    fiscalYear,
    sourceFile,
    lastIngestedAt,
    dataLimitations: "USCIS Employer Data Hub reflects first decisions only and excludes later appeals, revocations, and pending cases.",
    searchTokens: buildSearchTokens([employerName, city, state, taxIdLastFour]),
    totalWorkers: parseNumber(firstValue(row, "totalworkers", "beneficiaries", "totalbeneficiaries")),
    initialApprovals: parseNumber(firstValue(row, "initialapproval", "initialapprovals", "newemploymentapproval")),
    initialDenials: parseNumber(firstValue(row, "initialdenial", "initialdenials", "newemploymentdenial")),
    continuingApprovals: parseNumber(firstValue(row, "continuingapproval", "continuingapprovals", "continuingemploymentapproval")),
    continuingDenials: parseNumber(firstValue(row, "continuingdenial", "continuingdenials", "continuingemploymentdenial")),
    changeOfEmployerApprovals: parseNumber(firstValue(row, "changeofemployerapproval", "changeemployerapproval")),
    changeOfEmployerDenials: parseNumber(firstValue(row, "changeofemployerdenial", "changeemployerdenial")),
  };
}

function buildEmployerDocId(record: EmployerHubRecord): string {
  return [
    record.fiscalYear,
    record.normalizedEmployerName,
    record.normalizedCity || "na",
    normalizeLabel(record.state) || "na",
    record.taxIdLastFour || "xxxx",
  ].join("__").slice(0, 180);
}

function buildRowObject(header: string[], values: string[]): Record<string, string> {
  const row: Record<string, string> = {};
  header.forEach((key, index) => {
    row[key] = values[index] || "";
  });
  return row;
}

function splitCsvLine(line: string): string[] {
  const values: string[] = [];
  let current = "";
  let inQuotes = false;
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    const next = line[index + 1];
    if (char === "\"") {
      if (inQuotes && next === "\"") {
        current += "\"";
        index += 1;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === "," && !inQuotes) {
      values.push(current.trim());
      current = "";
    } else {
      current += char;
    }
  }
  values.push(current.trim());
  return values;
}

function firstValue(row: Record<string, string>, ...aliases: string[]): string {
  for (const alias of aliases) {
    const normalized = normalizeLabel(alias);
    if (row[normalized]) {
      return row[normalized];
    }
  }
  return "";
}

function buildSearchTokens(values: string[]): string[] {
  const tokens = new Set<string>();
  for (const value of values) {
    const normalized = normalizeLabel(value || "");
    if (!normalized) {
      continue;
    }
    tokens.add(normalized);
    normalized.split(/(?<=.{3})/).forEach((token) => {
      if (token.length >= 3) {
        tokens.add(token);
      }
    });
  }
  return [...tokens];
}

function parseNumber(value: string): number {
  const cleaned = `${value || ""}`.replace(/[^0-9.-]/g, "");
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) ? parsed : 0;
}
