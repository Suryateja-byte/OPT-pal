import { HttpsError, onCall } from "firebase-functions/v2/https";
import { PDFDocument, StandardFonts } from "pdf-lib";
import {
  documentRef,
  firestore,
  saveGeneratedSecureDocument,
} from "./documentSecurity";

type FicaRefundCaseStatus =
  | "intake"
  | "eligibility_ready"
  | "manual_review_required"
  | "employer_outreach"
  | "irs_packet_ready"
  | "closed_refunded"
  | "closed_out_of_scope";

type FicaEligibilityClassification =
  | "eligible"
  | "not_applicable"
  | "manual_review_required"
  | "out_of_scope";

type EmployerRefundOutcome =
  | ""
  | "refunded"
  | "promised_correction"
  | "refused"
  | "no_response";

type W2ExtractionDraft = {
  documentId: string;
  fileName: string;
  displayName: string;
  taxYear: number | null;
  employerName: string;
  employerEinMasked: string;
  employeeName: string;
  employeeSsnLast4: string;
  wagesBox1: number | null;
  federalWithholdingBox2: number | null;
  socialSecurityWagesBox3: number | null;
  socialSecurityTaxBox4: number | null;
  medicareWagesBox5: number | null;
  medicareTaxBox6: number | null;
  stateWageRows: Array<{stateCode: string; wages: number | null; withholding: number | null}>;
};

type FicaUserTaxInputs = {
  firstUsStudentTaxYear?: number | null;
  authorizedEmploymentConfirmed?: boolean;
  maintainedStudentStatusForEntireTaxYear?: boolean;
  noResidencyStatusChangeConfirmed?: boolean;
  currentMailingAddress?: string;
};

type FicaEligibilityResult = {
  classification: FicaEligibilityClassification;
  refundAmount: number | null;
  eligibilityReasons: string[];
  blockingIssues: string[];
  requiredAttachments: string[];
  recommendedNextStep: string;
  statuteWarning: string;
};

type FicaRefundPacket = {
  documentId: string;
  fileName: string;
  generatedAt: number;
  kind: "employer" | "irs";
};

type StoredCaseRecord = {
  w2DocumentId?: string;
  taxYear?: number;
  employerName?: string;
  userInputs?: FicaUserTaxInputs;
  employerOutcome?: EmployerRefundOutcome;
  eligibilityResult?: FicaEligibilityResult;
  employerPacket?: FicaRefundPacket;
  irsPacket?: FicaRefundPacket;
  status?: FicaRefundCaseStatus;
};

type StoredDocumentRecord = {
  fileName?: string;
  userTag?: string;
  documentType?: string;
  documentCategory?: string;
  chatEligible?: boolean;
  extractedData?: Record<string, unknown>;
};

type UserProfileRecord = {
  optType?: string;
  firstUsStudentTaxYear?: number;
  email?: string;
};

type TaxPolicy = {
  socialSecurityRate: number;
  medicareRate: number;
  socialSecurityWageBase: number;
  additionalMedicareThreshold: number;
};

const TAX_POLICY_BY_YEAR: Record<number, TaxPolicy> = {
  2022: {socialSecurityRate: 0.062, medicareRate: 0.0145, socialSecurityWageBase: 147000, additionalMedicareThreshold: 200000},
  2023: {socialSecurityRate: 0.062, medicareRate: 0.0145, socialSecurityWageBase: 160200, additionalMedicareThreshold: 200000},
  2024: {socialSecurityRate: 0.062, medicareRate: 0.0145, socialSecurityWageBase: 168600, additionalMedicareThreshold: 200000},
  2025: {socialSecurityRate: 0.062, medicareRate: 0.0145, socialSecurityWageBase: 176100, additionalMedicareThreshold: 200000},
  2026: {socialSecurityRate: 0.062, medicareRate: 0.0145, socialSecurityWageBase: 184500, additionalMedicareThreshold: 200000},
};

const TAX_SENSITIVE_CATEGORY = "tax_sensitive";
const EMPLOYER_NEXT_STEP =
  "Ask your employer or payroll provider to refund the Social Security and Medicare tax first. Use the employer packet and keep written proof of the response.";
const FICA_ATTACHMENTS = [
  "Copy of Form W-2 for the tax year at issue",
  "Copy of your visa and Form I-94",
  "Copy of your I-20 or OPT/STEM OPT authorization",
  "Signed statement or Form 8316 documenting that the employer would not refund the withholding",
];

export const evaluateFicaRefundCase = onCall({enforceAppCheck: true}, async (request) => {
  const userId = requireAuth(request.auth);
  const caseId = asNonEmptyString(request.data.caseId, "caseId");
  const taxCase = await loadCaseRecord(userId, caseId);
  const w2Document = await loadW2Document(userId, taxCase.w2DocumentId || "");
  const profile = await loadUserProfile(userId);
  const userInputs = normalizeUserInputs(taxCase.userInputs);
  const eligibility = buildFicaEligibilityResult({
    profile,
    inputs: userInputs,
    w2: w2Document,
    now: Date.now(),
  });

  const nextStatus = statusFromEligibility(eligibility.classification);
  await taxCaseRef(userId, caseId).set(
    {
      taxYear: w2Document.taxYear || 0,
      employerName: w2Document.employerName,
      eligibilityResult: eligibility,
      status: nextStatus,
      updatedAt: Date.now(),
    },
    {merge: true}
  );

  return eligibility;
});

export const generateFicaEmployerPacket = onCall({enforceAppCheck: true}, async (request) => {
  const userId = requireAuth(request.auth);
  const caseId = asNonEmptyString(request.data.caseId, "caseId");
  const taxCase = await loadCaseRecord(userId, caseId);
  const eligibility = requireEligibleResult(taxCase.eligibilityResult);
  const w2Document = await loadW2Document(userId, taxCase.w2DocumentId || "");
  const profile = await loadUserProfile(userId);
  const fileName = buildPacketFileName("employer", w2Document.taxYear, w2Document.employerName);
  const pdfBytes = await buildPdfPacket({
    title: "FICA Employer Refund Request",
    subtitle: `${w2Document.employerName} • Tax Year ${w2Document.taxYear || "Unknown"}`,
    sections: [
      {
        heading: "Summary",
        lines: [
          `Requested refund amount: ${formatCurrency(eligibility.refundAmount)}`,
          `Employee: ${w2Document.employeeName || profile.email || "Student employee"}`,
          `Reason: The app identified Social Security and/or Medicare tax withholding on authorized F-1/OPT/STEM employment while the student appears to be a nonresident alien for tax purposes.`,
        ],
      },
      {
        heading: "Employer Request",
        lines: [
          "Please review this withholding and refund any Social Security and Medicare tax withheld in error.",
          "If payroll records are corrected, issue the corrected wage statement or other written confirmation required for the employee's records.",
          "IRS source: Nonresident F-1 students are generally exempt from FICA on authorized student employment, including OPT and STEM OPT, until they become resident aliens for tax purposes.",
        ],
      },
      {
        heading: "Suggested Email / Letter",
        lines: [
          `I am requesting a refund of Social Security and Medicare tax withheld from my wages for tax year ${w2Document.taxYear || ""}.`,
          `My W-2 shows Social Security tax withheld of ${formatCurrency(w2Document.socialSecurityTaxBox4)} and Medicare tax withheld of ${formatCurrency(w2Document.medicareTaxBox6)}.`,
          "I was working in authorized F-1 student employment and believe I was exempt from FICA for that year as a nonresident alien.",
          "Please review the withholding, refund any tax withheld in error, and provide any corrected payroll documentation needed for my records.",
        ],
      },
      {
        heading: "References",
        lines: [
          "IRS Social Security Tax / Medicare Tax and Self-Employment",
          "https://www.irs.gov/individuals/international-taxpayers/social-security-tax-medicare-tax-and-self-employment",
          "Publication 4152",
          "https://www.irs.gov/pub/irs-access/p4152_accessible.pdf",
        ],
      },
    ],
  });

  const saved = await saveGeneratedSecureDocument({
    userId,
    fileName,
    userTag: `FICA Employer Packet ${w2Document.taxYear || ""}`.trim(),
    contentType: "application/pdf",
    documentCategory: TAX_SENSITIVE_CATEGORY,
    chatEligible: false,
    bytes: pdfBytes,
  });

  const packet: FicaRefundPacket = {
    documentId: saved.documentId,
    fileName,
    generatedAt: saved.uploadedAt,
    kind: "employer",
  };
  await taxCaseRef(userId, caseId).set(
    {
      employerPacket: packet,
      status: "employer_outreach",
      updatedAt: saved.uploadedAt,
    },
    {merge: true}
  );

  return {packet};
});

export const generateFicaRefundPacket = onCall({enforceAppCheck: true}, async (request) => {
  const userId = requireAuth(request.auth);
  const caseId = asNonEmptyString(request.data.caseId, "caseId");
  const fullSsn = normalizeDigits(request.data.fullSsn, 9, "fullSsn");
  const fullEmployerEin = normalizeDigits(request.data.fullEmployerEin, 9, "fullEmployerEin");
  const mailingAddress = asNonEmptyString(request.data.mailingAddress, "mailingAddress");

  const taxCase = await loadCaseRecord(userId, caseId);
  if (!["refused", "no_response"].includes(taxCase.employerOutcome || "")) {
    throw new HttpsError("failed-precondition", "IRS packet is available only after the employer refuses or does not respond.");
  }
  const eligibility = requireEligibleResult(taxCase.eligibilityResult);
  const w2Document = await loadW2Document(userId, taxCase.w2DocumentId || "");
  const fileName = buildPacketFileName("irs", w2Document.taxYear, w2Document.employerName);
  const filingDestination = buildFilingDestinationGuidance();
  const pdfBytes = await buildPdfPacket({
    title: "FICA IRS Claim Packet",
    subtitle: `${w2Document.employerName} • Tax Year ${w2Document.taxYear || "Unknown"}`,
    sections: [
      {
        heading: "Eligibility Summary",
        lines: [
          `Calculated refund amount: ${formatCurrency(eligibility.refundAmount)}`,
          ...eligibility.eligibilityReasons,
          eligibility.statuteWarning,
        ].filter(Boolean),
      },
      {
        heading: "Form 843 Field Map",
        lines: [
          `Name: ${w2Document.employeeName || "Confirm legal name"}`,
          `Taxpayer identification number: ${formatSsn(fullSsn)}`,
          `Current mailing address: ${mailingAddress}`,
          "Type of tax: Social Security and Medicare tax withheld in error",
          `Tax period: Calendar year ${w2Document.taxYear || ""}`,
          `Amount to refund: ${formatCurrency(eligibility.refundAmount)}`,
          "Explanation: FICA was withheld in error from authorized F-1 student employment while the taxpayer was a nonresident alien for tax purposes.",
        ],
      },
      {
        heading: "Form 8316 / Employer Statement",
        lines: [
          `Employer: ${w2Document.employerName}`,
          `Employer EIN: ${formatEin(fullEmployerEin)}`,
          `Employer response outcome: ${taxCase.employerOutcome || "refused"}`,
          "State that the employer was asked to refund the withholding and either refused or did not respond.",
        ],
      },
      {
        heading: "Required Attachments",
        lines: eligibility.requiredAttachments,
      },
      {
        heading: "Where To File",
        lines: [
          filingDestination,
          "Official IRS instructions: https://www.irs.gov/instructions/i843",
          "IRS FICA guidance: https://www.irs.gov/individuals/international-taxpayers/social-security-tax-medicare-tax-and-self-employment",
        ],
      },
    ],
  });

  const saved = await saveGeneratedSecureDocument({
    userId,
    fileName,
    userTag: `FICA IRS Packet ${w2Document.taxYear || ""}`.trim(),
    contentType: "application/pdf",
    documentCategory: TAX_SENSITIVE_CATEGORY,
    chatEligible: false,
    bytes: pdfBytes,
  });

  const packet: FicaRefundPacket = {
    documentId: saved.documentId,
    fileName,
    generatedAt: saved.uploadedAt,
    kind: "irs",
  };
  await taxCaseRef(userId, caseId).set(
    {
      irsPacket: packet,
      status: "irs_packet_ready",
      updatedAt: saved.uploadedAt,
    },
    {merge: true}
  );

  return {packet};
});

export function normalizeW2Extraction(input: {
  documentId: string;
  fileName?: string;
  userTag?: string;
  documentType?: string;
  extractedData?: Record<string, unknown>;
}): W2ExtractionDraft | null {
  const extractedData = toRecord(input.extractedData);
  const normalizedIndex = buildNormalizedIndex(extractedData);
  const looseType = normalizeLooseLabel(`${input.documentType || ""} ${input.userTag || ""} ${input.fileName || ""}`);
  const extractedType = normalizeLooseLabel(firstString(normalizedIndex, "document_type") || "");

  if (!looseType.includes("w2") && extractedType !== "w2" && extractedType !== "w2form") {
    return null;
  }

  return {
    documentId: input.documentId,
    fileName: input.fileName || "",
    displayName: input.userTag || input.fileName || "W-2",
    taxYear: asYear(firstValue(normalizedIndex, "tax_year", "year", "w2_year")),
    employerName: firstString(normalizedIndex, "employer_name", "employer") || "",
    employerEinMasked: maskEin(firstString(normalizedIndex, "employer_ein_masked", "employer_ein", "ein")),
    employeeName: firstString(normalizedIndex, "employee_name", "name") || "",
    employeeSsnLast4: normalizeLast4(firstString(normalizedIndex, "employee_ssn_last4", "employee_ssn", "ssn")),
    wagesBox1: asCurrency(firstValue(normalizedIndex, "wages_box1", "box_1", "box1", "wages")),
    federalWithholdingBox2: asCurrency(firstValue(normalizedIndex, "federal_withholding_box2", "box_2", "box2")),
    socialSecurityWagesBox3: asCurrency(firstValue(normalizedIndex, "social_security_wages_box3", "box_3", "box3")),
    socialSecurityTaxBox4: asCurrency(firstValue(normalizedIndex, "social_security_tax_box4", "box_4", "box4")),
    medicareWagesBox5: asCurrency(firstValue(normalizedIndex, "medicare_wages_box5", "box_5", "box5")),
    medicareTaxBox6: asCurrency(firstValue(normalizedIndex, "medicare_tax_box6", "box_6", "box6")),
    stateWageRows: asStateRows(firstValue(normalizedIndex, "state_wage_rows", "state_rows")),
  };
}

export function buildFicaEligibilityResult(input: {
  profile: UserProfileRecord;
  inputs: FicaUserTaxInputs;
  w2: W2ExtractionDraft;
  now: number;
}): FicaEligibilityResult {
  const taxYear = input.w2.taxYear;
  if (!taxYear) {
    return manualReviewResult("The W-2 tax year could not be confirmed from the uploaded document.");
  }

  const optType = `${input.profile.optType || ""}`.trim().toLowerCase();
  if (!["initial", "stem"].includes(optType)) {
    return {
      classification: "out_of_scope",
      refundAmount: null,
      eligibilityReasons: [],
      blockingIssues: ["This version supports F-1 OPT and STEM OPT cases only."],
      requiredAttachments: [],
      recommendedNextStep: "Use guidance only and consult a qualified tax professional if you are outside the supported F-1 scope.",
      statuteWarning: buildStatuteWarning(taxYear, input.now),
    };
  }

  const withholdingAmount = roundCurrency((input.w2.socialSecurityTaxBox4 || 0) + (input.w2.medicareTaxBox6 || 0));
  if (withholdingAmount <= 0) {
    return {
      classification: "not_applicable",
      refundAmount: 0,
      eligibilityReasons: ["The uploaded W-2 does not show Social Security or Medicare tax withholding in boxes 4 or 6."],
      blockingIssues: [],
      requiredAttachments: [],
      recommendedNextStep: "No FICA refund appears to be needed for this W-2.",
      statuteWarning: buildStatuteWarning(taxYear, input.now),
    };
  }

  const firstUsStudentTaxYear = input.inputs.firstUsStudentTaxYear ?? input.profile.firstUsStudentTaxYear ?? null;
  if (firstUsStudentTaxYear == null) {
    return manualReviewResult("Enter your first U.S. student tax year before the app can evaluate FICA eligibility.", taxYear, input.now);
  }
  if (!input.inputs.authorizedEmploymentConfirmed ||
    !input.inputs.maintainedStudentStatusForEntireTaxYear ||
    !input.inputs.noResidencyStatusChangeConfirmed) {
    return manualReviewResult(
      "Confirm authorized F-1 employment, continuous student status for the tax year, and no residency-status changes before eligibility can be auto-approved.",
      taxYear,
      input.now
    );
  }

  const exemptCalendarYearsUsed = taxYear - firstUsStudentTaxYear;
  if (exemptCalendarYearsUsed < 0) {
    return manualReviewResult("The first U.S. student tax year cannot be after the W-2 tax year.", taxYear, input.now);
  }
  if (exemptCalendarYearsUsed >= 5) {
    return manualReviewResult(
      "This appears to be the sixth-or-later calendar year in F-1 status, which needs manual tax review before claiming a FICA refund.",
      taxYear,
      input.now
    );
  }

  if (detectAdditionalMedicareComplexity(input.w2, taxYear)) {
    return manualReviewResult(
      "The W-2 suggests Additional Medicare complexity, so the app will not auto-calculate this refund.",
      taxYear,
      input.now
    );
  }

  return {
    classification: "eligible",
    refundAmount: withholdingAmount,
    eligibilityReasons: [
      `The W-2 shows Social Security and/or Medicare withholding totaling ${formatCurrency(withholdingAmount)}.`,
      "You confirmed this was authorized F-1 OPT/STEM employment for the tax year.",
      `Tax year ${taxYear} falls within the first five exempt-student calendar years based on the first U.S. student tax year provided.`,
    ],
    blockingIssues: [],
    requiredAttachments: FICA_ATTACHMENTS,
    recommendedNextStep: EMPLOYER_NEXT_STEP,
    statuteWarning: buildStatuteWarning(taxYear, input.now),
  };
}

export function detectAdditionalMedicareComplexity(w2: W2ExtractionDraft, taxYear: number | null): boolean {
  if (taxYear == null) {
    return false;
  }
  const policy = TAX_POLICY_BY_YEAR[taxYear];
  if (!policy) {
    return true;
  }
  const medicareWages = w2.medicareWagesBox5 || 0;
  const medicareTax = w2.medicareTaxBox6 || 0;
  const expectedBaseTax = roundCurrency(medicareWages * policy.medicareRate);
  return medicareWages >= policy.additionalMedicareThreshold || medicareTax > expectedBaseTax + 0.5;
}

function statusFromEligibility(classification: FicaEligibilityClassification): FicaRefundCaseStatus {
  switch (classification) {
  case "eligible":
    return "eligibility_ready";
  case "not_applicable":
  case "out_of_scope":
    return "closed_out_of_scope";
  case "manual_review_required":
  default:
    return "manual_review_required";
  }
}

function manualReviewResult(message: string, taxYear?: number | null, now: number = Date.now()): FicaEligibilityResult {
  return {
    classification: "manual_review_required",
    refundAmount: null,
    eligibilityReasons: [],
    blockingIssues: [message],
    requiredAttachments: [],
    recommendedNextStep: "Use the workflow guidance only and consult a qualified tax professional before filing a refund claim.",
    statuteWarning: buildStatuteWarning(taxYear || null, now),
  };
}

function requireEligibleResult(result?: FicaEligibilityResult | null): FicaEligibilityResult {
  if (!result || result.classification !== "eligible") {
    throw new HttpsError("failed-precondition", "This refund case is not eligible for packet generation.");
  }
  return result;
}

function buildPacketFileName(kind: "employer" | "irs", taxYear: number | null, employerName: string): string {
  const safeEmployer = employerName.replace(/[^A-Za-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 40) || "employer";
  const year = taxYear || "tax-year";
  return kind === "employer" ?
    `fica-employer-packet-${year}-${safeEmployer}.pdf` :
    `fica-irs-packet-${year}-${safeEmployer}.pdf`;
}

function buildStatuteWarning(taxYear: number | null, now: number): string {
  if (!taxYear) {
    return "Review the IRS 3-year / 2-year refund deadline before filing.";
  }
  const deadline = Date.UTC(taxYear + 4, 3, 15);
  if (now > deadline) {
    return `The standard 3-year refund window for tax year ${taxYear} appears to have passed. Review the IRS 3-year / 2-year deadline carefully before filing.`;
  }
  const daysRemaining = Math.ceil((deadline - now) / 86_400_000);
  if (daysRemaining <= 90) {
    return `The standard 3-year refund window for tax year ${taxYear} appears to close on ${formatUtcDate(deadline)}. File promptly if the employer will not refund the withholding.`;
  }
  return `Under the standard 3-year / 2-year refund rule, tax year ${taxYear} is typically timely until ${formatUtcDate(deadline)} if your return was timely filed.`;
}

function buildFilingDestinationGuidance(): string {
  return "Mail Form 843 and attachments to the IRS office where your employer's Forms 941 were filed. Confirm the current mailing address with the employer or current IRS instructions before sending the packet.";
}

async function buildPdfPacket(input: {
  title: string;
  subtitle: string;
  sections: Array<{heading: string; lines: string[]}>;
}): Promise<Buffer> {
  const pdf = await PDFDocument.create();
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const boldFont = await pdf.embedFont(StandardFonts.HelveticaBold);
  const pageWidth = 612;
  const pageHeight = 792;
  const margin = 50;
  let page = pdf.addPage([pageWidth, pageHeight]);
  let y = pageHeight - margin;

  const newPage = () => {
    page = pdf.addPage([pageWidth, pageHeight]);
    y = pageHeight - margin;
  };

  const drawWrapped = (text: string, size: number, isBold: boolean = false) => {
    const fontToUse = isBold ? boldFont : font;
    const lines = wrapText(text, fontToUse, size, pageWidth - margin * 2);
    for (const line of lines) {
      if (y < margin + 24) {
        newPage();
      }
      page.drawText(line, {
        x: margin,
        y,
        size,
        font: fontToUse,
      });
      y -= size + 4;
    }
  };

  drawWrapped(input.title, 20, true);
  y -= 4;
  drawWrapped(input.subtitle, 12, false);
  y -= 12;

  for (const section of input.sections) {
    drawWrapped(section.heading, 14, true);
    y -= 2;
    for (const line of section.lines) {
      drawWrapped(`• ${line}`, 11, false);
    }
    y -= 10;
  }

  const bytes = await pdf.save();
  return Buffer.from(bytes);
}

function wrapText(text: string, font: any, fontSize: number, maxWidth: number): string[] {
  const words = text.split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return [""];
  }
  const lines: string[] = [];
  let current = "";
  for (const word of words) {
    const next = current ? `${current} ${word}` : word;
    if (font.widthOfTextAtSize(next, fontSize) <= maxWidth) {
      current = next;
      continue;
    }
    if (current) {
      lines.push(current);
    }
    current = word;
  }
  if (current) {
    lines.push(current);
  }
  return lines;
}

async function loadCaseRecord(userId: string, caseId: string): Promise<StoredCaseRecord> {
  const snapshot = await taxCaseRef(userId, caseId).get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "FICA refund case not found.");
  }
  return snapshot.data() as StoredCaseRecord;
}

async function loadW2Document(userId: string, documentId: string): Promise<W2ExtractionDraft> {
  if (!documentId) {
    throw new HttpsError("failed-precondition", "A W-2 document is required for this refund case.");
  }
  const snapshot = await documentRef(userId, documentId).get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Linked W-2 document not found.");
  }
  const raw = snapshot.data() as StoredDocumentRecord;
  const parsed = normalizeW2Extraction({
    documentId,
    fileName: raw.fileName,
    userTag: raw.userTag,
    documentType: raw.documentType,
    extractedData: raw.extractedData,
  });
  if (!parsed) {
    throw new HttpsError("failed-precondition", "The linked document is not a processed W-2.");
  }
  return parsed;
}

async function loadUserProfile(userId: string): Promise<UserProfileRecord> {
  const snapshot = await firestore.collection("users").doc(userId).get();
  if (!snapshot.exists) {
    return {};
  }
  const data = snapshot.data() || {};
  return {
    optType: typeof data.optType === "string" ? data.optType : "",
    firstUsStudentTaxYear: typeof data.firstUsStudentTaxYear === "number" ? data.firstUsStudentTaxYear : undefined,
    email: typeof data.email === "string" ? data.email : "",
  };
}

function taxCaseRef(userId: string, caseId: string) {
  return firestore.collection("users").doc(userId).collection("ficaRefundCases").doc(caseId);
}

function normalizeUserInputs(value: unknown): FicaUserTaxInputs {
  const record = toRecord(value);
  return {
    firstUsStudentTaxYear: asYear(record.firstUsStudentTaxYear),
    authorizedEmploymentConfirmed: Boolean(record.authorizedEmploymentConfirmed),
    maintainedStudentStatusForEntireTaxYear: Boolean(record.maintainedStudentStatusForEntireTaxYear),
    noResidencyStatusChangeConfirmed: Boolean(record.noResidencyStatusChangeConfirmed),
    currentMailingAddress: typeof record.currentMailingAddress === "string" ? record.currentMailingAddress.trim() : "",
  };
}

function buildNormalizedIndex(data: Record<string, unknown>): Map<string, unknown> {
  const index = new Map<string, unknown>();
  for (const [key, value] of Object.entries(data)) {
    index.set(normalizeLooseLabel(key), value);
  }
  return index;
}

function firstValue(index: Map<string, unknown>, ...aliases: string[]): unknown {
  for (const alias of aliases) {
    const value = index.get(normalizeLooseLabel(alias));
    if (value !== undefined && value !== null && `${value}`.trim()) {
      return value;
    }
  }
  return null;
}

function firstString(index: Map<string, unknown>, ...aliases: string[]): string | null {
  const value = firstValue(index, ...aliases);
  return value == null ? null : `${value}`.trim() || null;
}

function asCurrency(value: unknown): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return roundCurrency(value);
  }
  const raw = `${value}`.replace(/[$,\s]/g, "");
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? roundCurrency(parsed) : null;
}

function asYear(value: unknown): number | null {
  if (typeof value === "number" && Number.isInteger(value)) {
    return value;
  }
  const raw = `${value || ""}`.trim();
  if (!/^\d{4}$/.test(raw)) {
    return null;
  }
  return Number(raw);
}

function asStateRows(value: unknown): Array<{stateCode: string; wages: number | null; withholding: number | null}> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((row) => {
    const record = toRecord(row);
    return {
      stateCode: typeof record.stateCode === "string" ? record.stateCode : typeof record.state_code === "string" ? record.state_code : "",
      wages: asCurrency(record.wages),
      withholding: asCurrency(record.withholding),
    };
  });
}

function normalizeLast4(value: string | null): string {
  const digits = `${value || ""}`.replace(/\D/g, "");
  return digits.length >= 4 ? digits.slice(-4) : "";
}

function maskEin(value: string | null): string {
  const digits = `${value || ""}`.replace(/\D/g, "");
  if (digits.length < 9) {
    return "";
  }
  return `XX-XXX${digits.slice(-4)}`;
}

function normalizeLooseLabel(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function toRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function requireAuth(auth?: {uid?: string} | null): string {
  if (!auth?.uid) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }
  return auth.uid;
}

function asNonEmptyString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new HttpsError("invalid-argument", `${fieldName} is required.`);
  }
  return value.trim();
}

function normalizeDigits(value: unknown, length: number, fieldName: string): string {
  const digits = `${value || ""}`.replace(/\D/g, "");
  if (digits.length !== length) {
    throw new HttpsError("invalid-argument", `${fieldName} must contain ${length} digits.`);
  }
  return digits;
}

function roundCurrency(value: number): number {
  return Math.round(value * 100) / 100;
}

function formatCurrency(value: number | null | undefined): string {
  if (value == null) {
    return "Confirm amount";
  }
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(value);
}

function formatUtcDate(timestamp: number): string {
  return new Date(timestamp).toISOString().slice(0, 10);
}

function formatSsn(value: string): string {
  return `${value.slice(0, 3)}-${value.slice(3, 5)}-${value.slice(5)}`;
}

function formatEin(value: string): string {
  return `${value.slice(0, 2)}-${value.slice(2)}`;
}
