import { HttpsError, onCall } from "firebase-functions/v2/https";
import { VertexAI, SchemaType } from "@google-cloud/vertexai";
import { createHash } from "crypto";
import { readFileSync } from "fs";
import * as path from "path";
import { PDFDocument } from "pdf-lib";
import { firestore, saveGeneratedSecureDocument } from "./documentSecurity";

const vertexAI = new VertexAI({
  project: process.env.GCLOUD_PROJECT || "opt-pal",
  location: "us-central1",
});
const model = vertexAI.getGenerativeModel({model: "gemini-2.5-flash"});
const POLICY_VERSION = "2026-03-10";
const LAST_REVIEWED_AT = Date.parse("2026-03-10T00:00:00Z");
const TEMPLATE_VERSION = "ice-i983-2026-03";
const TEMPLATE_PATH = path.join(__dirname, "../assets/i983.pdf");
const TEMPLATE_BYTES = readFileSync(TEMPLATE_PATH);
const TEMPLATE_SHA256 = createHash("sha256").update(TEMPLATE_BYTES).digest("hex");

type I983Record = Record<string, any>;

export const getI983AssistantBundle = onCall(async (request) => {
  requireAuth(request.auth?.uid);
  return buildI983AssistantBundle();
});

export const generateI983SectionDrafts = onCall(async (request) => {
  const userId = requireAuth(request.auth?.uid);
  const draftId = asNonEmptyString(request.data.draftId, "draftId");
  const selectedDocumentIds = Array.isArray(request.data.selectedDocumentIds) ?
    request.data.selectedDocumentIds.filter((value: unknown): value is string => typeof value === "string") : [];
  const draft = await loadDraft(userId, draftId);
  const profile = await loadUserProfile(userId);
  const documentContext = await loadSelectedDocumentContext(userId, selectedDocumentIds);
  const prompt = `
You are helping draft Form I-983 narratives for a STEM OPT student.
Return valid JSON only.
If the role-to-degree relationship is weak, the employer pattern looks risky, or the information is too incomplete, set classification to "consult_dso_attorney".
Keep every field concise and suitable for the official form.
Student major: ${profile.majorName || ""}
Workflow: ${draft.workflowType || ""}
Student role input: ${draft.trainingPlanSection?.studentRole || ""}
Goals input: ${draft.trainingPlanSection?.goalsAndObjectives || ""}
Oversight input: ${draft.trainingPlanSection?.employerOversight || ""}
Measures input: ${draft.trainingPlanSection?.measuresAndAssessments || ""}
Hours per week: ${draft.employerSection?.hoursPerWeek || ""}
Employer: ${draft.employerSection?.employerName || ""}
Documents:
${documentContext}
`;
  const result = await model.generateContent({
    contents: [{role: "user", parts: [{text: prompt}]}],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: {
        type: SchemaType.OBJECT,
        required: ["classification", "confidence", "studentRole", "goalsAndObjectives", "employerOversight", "measuresAndAssessments", "annualEvaluation", "finalEvaluation", "missingInputs", "warnings"],
        properties: {
          classification: {type: SchemaType.STRING, enum: ["draft_assistance", "consult_dso_attorney"]},
          confidence: {type: SchemaType.STRING, enum: ["low", "medium", "high"]},
          studentRole: {type: SchemaType.STRING},
          goalsAndObjectives: {type: SchemaType.STRING},
          employerOversight: {type: SchemaType.STRING},
          measuresAndAssessments: {type: SchemaType.STRING},
          annualEvaluation: {type: SchemaType.STRING},
          finalEvaluation: {type: SchemaType.STRING},
          missingInputs: {type: SchemaType.ARRAY, items: {type: SchemaType.STRING}},
          warnings: {type: SchemaType.ARRAY, items: {type: SchemaType.STRING}},
        },
      },
      maxOutputTokens: 900,
    },
  });
  const text = result.response.candidates?.[0]?.content?.parts?.[0]?.text || "{}";
  const parsed = normalizeNarrative(parseJsonRecord(text));
  await draftRef(userId, draftId).set({selectedDocumentIds, generatedNarrative: parsed, updatedAt: Date.now()}, {merge: true});
  return parsed;
});

export const exportI983OfficialPdf = onCall(async (request) => {
  const userId = requireAuth(request.auth?.uid);
  const draftId = asNonEmptyString(request.data.draftId, "draftId");
  const draft = await loadDraft(userId, draftId);
  const pdfBytes = await buildOfficialI983Pdf(draft);
  const saved = await saveGeneratedSecureDocument({
    userId,
    fileName: `${sanitizeFileSegment(draft.employerSection?.employerName || "i983")}-${draftId}.pdf`,
    userTag: `I-983 Export ${draft.workflowType || ""}`.trim(),
    contentType: "application/pdf",
    documentCategory: "general",
    chatEligible: false,
    bytes: pdfBytes,
  });
  await draftRef(userId, draftId).set({
    latestExportDocumentId: saved.documentId,
    exportedAt: saved.uploadedAt,
    status: "exported",
    templateVersion: TEMPLATE_VERSION,
    policyVersion: POLICY_VERSION,
    updatedAt: saved.uploadedAt,
  }, {merge: true});
  return {
    documentId: saved.documentId,
    fileName: `${sanitizeFileSegment(draft.employerSection?.employerName || "i983")}-${draftId}.pdf`,
    generatedAt: saved.uploadedAt,
    templateVersion: TEMPLATE_VERSION,
  };
});

export function buildI983AssistantBundle() {
  return {
    version: POLICY_VERSION,
    generatedAt: Date.now(),
    lastReviewedAt: LAST_REVIEWED_AT,
    templateVersion: TEMPLATE_VERSION,
    templateSha256: TEMPLATE_SHA256,
    signatureGuidance: "Electronic signatures are permitted, but this export leaves all signature fields blank in v1.",
    staleAfterDays: 30,
    sources: [
      source("students", "Students and the Form I-983", "https://studyinthestates.dhs.gov/students/students-and-the-form-i-983"),
      source("employers", "Employers and the Form I-983", "https://studyinthestates.dhs.gov/employers-and-the-form-i-983"),
      source("dsos", "DSOs and the Form I-983", "https://studyinthestates.dhs.gov/schools/dsos-and-the-form-i-983"),
      source("overview", "Form I-983 Overview", "https://studyinthestates.dhs.gov/students/form-i-983-overview"),
      source("esign", "Sign and Send the Form I-983 Electronically", "https://studyinthestates.dhs.gov/2023/09/new-sign-and-send-the-form-i-983-electronically"),
      source("official_form", "Official Form I-983 PDF", "https://www.ice.gov/doclib/sevis/pdf/i983.pdf"),
    ],
    requirements: [
      requirement("initial_stem_extension", ["students", "overview", "official_form"]),
      requirement("new_employer", ["students", "employers", "official_form"]),
      requirement("material_change", ["students", "employers", "official_form"]),
      requirement("annual_evaluation", ["students", "dsos", "official_form"]),
      requirement("final_evaluation", ["students", "dsos", "official_form"]),
    ],
  };
}

function source(id: string, label: string, url: string) {
  return {id, label, url, effectiveDate: "2026-03-10", lastReviewedDate: "2026-03-10", summary: label};
}

function requirement(workflowType: string, citationIds: string[]) {
  return {workflowType, title: workflowType.replace(/_/g, " "), summary: "Official I-983 workflow requirements.", citationIds};
}

export async function buildOfficialI983Pdf(draft: I983Record): Promise<Buffer> {
  const pdf = await PDFDocument.load(TEMPLATE_BYTES);
  const form = pdf.getForm();
  setText(form, "Student Name SurnamePrimary Name Given Name", draft.studentSection?.studentName);
  setText(form, "Student Email Address", draft.studentSection?.studentEmailAddress);
  setText(form, "Name of School Recommending STEM OPT", draft.studentSection?.schoolRecommendingStemOpt);
  setText(form, "Name of School Where STEM Degree Was Earned", draft.studentSection?.schoolWhereDegreeWasEarned);
  setText(form, "SEVIS School Code of School Recommending STEM OPT including 3 digit suffix", draft.studentSection?.sevisSchoolCode);
  setText(form, "Designated School Official DSO Name and Contact Information", draft.studentSection?.dsoNameAndContact);
  setText(form, "Student SEVIS ID No", draft.studentSection?.studentSevisId);
  setText(form, "From", formatDate(draft.studentSection?.requestedStartDate));
  setText(form, "To", formatDate(draft.studentSection?.requestedEndDate));
  setText(form, "Qualifying Major and Classification of Instructional Programs CIP Code", draft.studentSection?.qualifyingMajorAndCipCode);
  setText(form, "LevelType of Qualifying Degree", draft.studentSection?.degreeLevel);
  setText(form, "Date Awarded mmddyyyy", formatDate(draft.studentSection?.degreeAwardedDate));
  setText(form, "Employment Authorization Number", draft.studentSection?.employmentAuthorizationNumber);
  setText(form, "Printed Name of Student", draft.studentSection?.studentName);
  setText(form, "Employer Name", draft.employerSection?.employerName);
  setText(form, "Street Address", draft.employerSection?.streetAddress);
  setText(form, "Suite", draft.employerSection?.suite);
  setText(form, "Employer Website URL", draft.employerSection?.employerWebsiteUrl);
  setText(form, "City", draft.employerSection?.city);
  setText(form, "State", draft.employerSection?.state);
  setText(form, "ZIP Code", draft.employerSection?.zipCode);
  setText(form, "Employer ID Number EIN", draft.employerSection?.employerEin);
  setText(form, "Number of FullTime Employees in US", draft.employerSection?.fullTimeEmployeesInUs);
  setText(form, "North American Industry Classification System NAICS Code", draft.employerSection?.naicsCode);
  setText(form, "OPT Hours Per Week must be at least 20 hoursweek", numberString(draft.employerSection?.hoursPerWeek));
  setText(form, "A Salary Amount and Frequency", draft.employerSection?.salaryAmountAndFrequency);
  setText(form, "Start Date of Employment mmddyyyy", formatDate(draft.employerSection?.employmentStartDate));
  setText(form, "1 1", draft.employerSection?.otherCompensationLine1);
  setText(form, "1 2", draft.employerSection?.otherCompensationLine2);
  setText(form, "2", draft.employerSection?.otherCompensationLine3);
  setText(form, "3", draft.employerSection?.otherCompensationLine4);
  setText(form, "Printed Name and Title of Employer Official with Signatory Authority", draft.employerSection?.employerOfficialNameAndTitle);
  setText(form, "Printed Name of Employing Organization", draft.employerSection?.employingOrganizationName);
  setText(form, "Student Name SurnamePrimary Name Given Name_2", draft.studentSection?.studentName);
  setText(form, "Employer Name_2", draft.employerSection?.employerName);
  setText(form, "Site Name", draft.trainingPlanSection?.siteName);
  setText(form, "Site Address Street City State ZIP", draft.trainingPlanSection?.siteAddress);
  setText(form, "Name of Official", draft.trainingPlanSection?.officialName);
  setText(form, "Official s Title", draft.trainingPlanSection?.officialTitle);
  setText(form, "Official s Email", draft.trainingPlanSection?.officialEmail);
  setText(form, "Official s Phone Number", draft.trainingPlanSection?.officialPhoneNumber);
  setText(form, "Student Role Describe the students role with the employer and how that role is directly related to enhancing the student s knowledge obtained through his or her qualifying STEM degree", draft.trainingPlanSection?.studentRole);
  setText(form, "Goals and Objectives Describe how the assignments with the employer will help the student achieve his or her specific objectives for workbased learning related to his or her STEM degree The description must both specify the students goals regarding specific knowledge skills or techniques as well as the means by which they will be achieved", draft.trainingPlanSection?.goalsAndObjectives);
  setText(form, "Employer Oversight Explain how the employer provides oversight and supervision of individuals filling positions such as that being filled by the named F1 student If the employer has a training program or related policy in place that controls such oversight and supervision please describe", draft.trainingPlanSection?.employerOversight);
  setText(form, "Measures and Assessments Explain how the employer measures and confirms whether individuals filling positions such as that being filled by the named F1 student are acquiring new knowledge and skills If the employer has a training program or related policy in place that controls such measures and assessments please describe", draft.trainingPlanSection?.measuresAndAssessments);
  setText(form, "Additional Remarks optional Provide additional information pertinent to the Plan", draft.trainingPlanSection?.additionalRemarks);
  setText(form, "Printed Name and Title of Employer Official with Signatory Authority_2", draft.employerSection?.employerOfficialNameAndTitle);
  setText(form, "undefined_3", formatDate(draft.evaluationSection?.annualEvaluationFromDate));
  setText(form, "undefined_4", formatDate(draft.evaluationSection?.annualEvaluationToDate));
  setText(form, "Printed Name of Student_2", draft.studentSection?.studentName);
  setText(form, "Printed Name of Employer Official with Signatory Authority", draft.trainingPlanSection?.officialName);
  setText(form, "undefined_5", formatDate(draft.evaluationSection?.finalEvaluationFromDate));
  setText(form, "undefined_6", formatDate(draft.evaluationSection?.finalEvaluationToDate));
  setText(form, "Printed Name of Student_3", draft.studentSection?.studentName);
  setText(form, "ty", draft.trainingPlanSection?.officialName);
  form.updateFieldAppearances();
  return Buffer.from(await pdf.save());
}

function setText(form: any, fieldName: string, value: unknown) {
  if (value == null || `${value}`.trim() === "") return;
  try { form.getTextField(fieldName).setText(`${value}`.trim().slice(0, 4000)); } catch {}
}

function formatDate(value: unknown): string {
  const millis = typeof value === "number" ? value : 0;
  if (!millis) return "";
  const date = new Date(millis);
  const month = `${date.getUTCMonth() + 1}`.padStart(2, "0");
  const day = `${date.getUTCDate()}`.padStart(2, "0");
  return `${month}/${day}/${date.getUTCFullYear()}`;
}

function numberString(value: unknown): string {
  return typeof value === "number" && Number.isFinite(value) ? `${value}` : "";
}

export function normalizeNarrative(raw: Record<string, unknown>) {
  const classification = raw.classification === "consult_dso_attorney" ? "consult_dso_attorney" : "draft_assistance";
  return {
    classification,
    confidence: ["low", "medium", "high"].includes(`${raw.confidence || ""}`) ? raw.confidence : "low",
    studentRole: safeText(raw.studentRole),
    goalsAndObjectives: safeText(raw.goalsAndObjectives),
    employerOversight: safeText(raw.employerOversight),
    measuresAndAssessments: safeText(raw.measuresAndAssessments),
    annualEvaluation: safeText(raw.annualEvaluation),
    finalEvaluation: safeText(raw.finalEvaluation),
    missingInputs: asStringArray(raw.missingInputs),
    warnings: asStringArray(raw.warnings),
  };
}

function safeText(value: unknown): string {
  return `${value || ""}`.replace(/\s+/g, " ").trim().slice(0, 2000);
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string").map((item) => item.trim()).filter(Boolean) : [];
}

function parseJsonRecord(text: string): Record<string, unknown> {
  try { return JSON.parse(text.replace(/```json/g, "").replace(/```/g, "").trim()) as Record<string, unknown>; } catch { return {}; }
}

async function loadDraft(userId: string, draftId: string): Promise<I983Record> {
  const snapshot = await draftRef(userId, draftId).get();
  if (!snapshot.exists) throw new HttpsError("not-found", "I-983 draft not found.");
  return snapshot.data() || {};
}

async function loadUserProfile(userId: string): Promise<Record<string, unknown>> {
  return (await firestore.collection("users").doc(userId).get()).data() || {};
}

async function loadSelectedDocumentContext(userId: string, documentIds: string[]): Promise<string> {
  if (documentIds.length === 0) return "No extra documents selected.";
  const docs = await Promise.all(documentIds.map(async (documentId) => (await firestore.collection("users").doc(userId).collection("documents").doc(documentId).get()).data() || {}));
  return docs.map((doc) => JSON.stringify({documentType: doc.documentType, summary: doc.summary, extractedData: doc.extractedData || {}})).join("\n");
}

function draftRef(userId: string, draftId: string) {
  return firestore.collection("users").doc(userId).collection("i983Drafts").doc(draftId);
}

function requireAuth(uid?: string): string {
  if (!uid) throw new HttpsError("unauthenticated", "User must be logged in.");
  return uid;
}

function asNonEmptyString(value: unknown, field: string): string {
  if (typeof value === "string" && value.trim()) return value.trim();
  throw new HttpsError("invalid-argument", `${field} is required.`);
}

function sanitizeFileSegment(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "") || "i983";
}
