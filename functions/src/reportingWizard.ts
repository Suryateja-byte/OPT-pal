import { HttpsError, onCall } from "firebase-functions/v2/https";
import { VertexAI, SchemaType } from "@google-cloud/vertexai";
import { randomUUID } from "crypto";
import { firestore } from "./documentSecurity";

const vertexAI = new VertexAI({
  project: process.env.GCLOUD_PROJECT || "opt-pal",
  location: "us-central1",
});
const model = vertexAI.getGenerativeModel({model: "gemini-2.5-flash"});

const POLICY_VERSION = "2026-03-10";
const POLICY_LAST_REVIEWED_AT = Date.parse("2026-03-10T00:00:00Z");
const DAY_MS = 86_400_000;
const MAJOR_RELATIONSHIP_SOURCE_LABEL = "Study in the States: Direct relationship between employment and major";
const MAJOR_RELATIONSHIP_SOURCE_URL =
  "https://studyinthestates.dhs.gov/students/direct-relationship-between-employment-and-students-major-area-of-study";
const POST_COMPLETION_SOURCE_LABEL = "Study in the States: Report your post-completion OPT employment information";
const POST_COMPLETION_SOURCE_URL =
  "https://studyinthestates.dhs.gov/students/report-your-post-completion-opt-employment-information";
const STEM_SOURCE_LABEL = "Study in the States: Reporting requirements for STEM OPT";
const STEM_SOURCE_URL =
  "https://studyinthestates.dhs.gov/students/reporting-requirements-for-stem-opt";
const PORTAL_GUIDE_SOURCE_LABEL = "SEVP Portal Student User Guide";
const PORTAL_GUIDE_SOURCE_URL =
  "https://studyinthestates.dhs.gov/sites/default/files/SEVP%20Portal%20Student%20User%20Guide.pdf";

type WizardEventType = "new_employer" | "employment_ended" | "material_change";
type WizardOptRegime = "post_completion" | "stem";
type WizardStatus = "drafting" | "ready" | "completed" | "cancelled";
type DraftClassification = "draft_assistance" | "consult_dso_attorney";
type DraftConfidence = "low" | "medium" | "high";

type ChecklistItem = {
  actor: "student" | "dso";
  title: string;
  details: string;
  sourceLabel: string;
  sourceUrl: string;
};

type ReportingWizardInput = {
  employerName?: string;
  jobTitle?: string;
  majorName?: string;
  worksiteAddress?: string;
  siteName?: string;
  supervisorName?: string;
  supervisorEmail?: string;
  supervisorPhone?: string;
  jobDuties?: string;
  toolsAndSkills?: string;
  userExplanationNotes?: string;
  hoursPerWeek?: number | null;
};

type ReportingWizardRecord = {
  eventType: WizardEventType;
  optRegime: WizardOptRegime;
  status: WizardStatus;
  eventDate: number;
  dueDate: number;
  obligationId: string;
  relatedEmploymentId: string;
  userInputs: ReportingWizardInput;
  generatedChecklist: ChecklistItem[];
  generatedDraft: Record<string, unknown> | null;
  editedDraft: string;
  policyVersion: string;
  policyLastReviewedAt: number;
  generatedAt: number | null;
  copiedAt: number | null;
  completedAt: number | null;
};

type DraftResult = {
  classification: DraftClassification;
  confidence: DraftConfidence;
  draftParagraph: string;
  whyThisDraftFits: string[];
  missingInputs: string[];
  warnings: string[];
};

type ReportingDocumentRecord = {
  id: string;
  processingMode?: string;
  processingStatus?: string;
  extractedData?: unknown;
  documentType?: string;
  summary?: string;
};

export const prepareReportingWizard = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  const userId = request.auth.uid;
  const obligationId = asOptionalString(request.data.obligationId);

  if (obligationId) {
    return seedWizardFromObligation(userId, obligationId);
  }

  const eventType = normalizeWizardEventType(request.data.eventType);
  const relatedEmploymentId = asNonEmptyString(request.data.relatedEmploymentId, "relatedEmploymentId");
  const eventDate = asNumber(request.data.eventDate, "eventDate");
  return createWizardForEvent(userId, eventType, relatedEmploymentId, eventDate);
});

export const completeReportingWizard = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  const userId = request.auth.uid;
  const wizardId = asNonEmptyString(request.data.wizardId, "wizardId");
  const wizardRef = firestore.collection("users").doc(userId).collection("reportingWizards").doc(wizardId);
  const wizardSnapshot = await wizardRef.get();
  if (!wizardSnapshot.exists) {
    throw new HttpsError("not-found", "Reporting wizard not found.");
  }

  const wizard = wizardSnapshot.data() as Partial<ReportingWizardRecord>;
  const completedAt = Date.now();
  await wizardRef.set(
    {
      status: "completed",
      completedAt,
    },
    {merge: true}
  );

  const obligationId = typeof wizard.obligationId === "string" ? wizard.obligationId : "";
  if (obligationId) {
    await firestore.collection("users").doc(userId).collection("reporting").doc(obligationId).set(
      {
        isCompleted: true,
      },
      {merge: true}
    );
  }

  return {completedAt};
});

export const generateSevpRelationshipDraft = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  const userId = request.auth.uid;
  const wizardId = asNonEmptyString(request.data.wizardId, "wizardId");
  const selectedDocumentIds = Array.isArray(request.data.selectedDocumentIds) ?
    request.data.selectedDocumentIds.filter((value: unknown): value is string => typeof value === "string") :
    [];

  const wizardRef = firestore.collection("users").doc(userId).collection("reportingWizards").doc(wizardId);
  const wizardSnapshot = await wizardRef.get();
  if (!wizardSnapshot.exists) {
    throw new HttpsError("not-found", "Reporting wizard not found.");
  }

  const wizard = wizardSnapshot.data() as Partial<ReportingWizardRecord>;
  const userInputs = toWizardInput(wizard.userInputs);
  const profileSnapshot = await firestore.collection("users").doc(userId).get();
  const profile = profileSnapshot.data() || {};
  const profileMajorName = asOptionalString(profile.majorName);
  const majorName = userInputs.majorName?.trim() || profileMajorName || "";
  const jobDuties = userInputs.jobDuties?.trim() || "";

  const missingInputs = [
    ...(majorName ? [] : ["majorName"]),
    ...(jobDuties ? [] : ["jobDuties"]),
  ];
  if (missingInputs.length > 0) {
    const blockedResult: DraftResult = {
      classification: "draft_assistance",
      confidence: "low",
      draftParagraph: "",
      whyThisDraftFits: [],
      missingInputs,
      warnings: [
        "Add your major name and describe your job duties before generating a draft.",
      ],
    };
    await wizardRef.set(
      {
        generatedDraft: blockedResult,
        generatedAt: Date.now(),
      },
      {merge: true}
    );
    return blockedResult;
  }

  const documentContext = await loadReportingDocumentContext(userId, selectedDocumentIds, profile);
  const eventType = normalizeWizardEventType(wizard.eventType);
  const optRegime = normalizeWizardOptRegime(wizard.optRegime);
  const draftWarnings = buildDraftWarnings(optRegime, userInputs.hoursPerWeek ?? null);

  const requestPayload = {
    contents: [
      {
        role: "user",
        parts: [
          {
            text: buildDraftPrompt({
              eventType,
              optRegime,
              majorName,
              userInputs,
              documentContext,
            }),
          },
        ],
      },
    ],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: {
        type: SchemaType.OBJECT,
        required: ["classification", "confidence", "draftParagraph", "whyThisDraftFits", "missingInputs", "warnings"],
        properties: {
          classification: {
            type: SchemaType.STRING,
            enum: ["draft_assistance", "consult_dso_attorney"],
          },
          confidence: {
            type: SchemaType.STRING,
            enum: ["low", "medium", "high"],
          },
          draftParagraph: {type: SchemaType.STRING},
          whyThisDraftFits: {
            type: SchemaType.ARRAY,
            items: {type: SchemaType.STRING},
          },
          missingInputs: {
            type: SchemaType.ARRAY,
            items: {type: SchemaType.STRING},
          },
          warnings: {
            type: SchemaType.ARRAY,
            items: {type: SchemaType.STRING},
          },
        },
      },
      maxOutputTokens: 700,
    },
  };

  const result = await model.generateContent(requestPayload);
  const text = result.response.candidates?.[0]?.content?.parts?.[0]?.text || "{}";
  const parsed = normalizeDraftResult(parseJsonRecord(text), draftWarnings);

  await wizardRef.set(
    {
      generatedDraft: parsed,
      generatedAt: Date.now(),
    },
    {merge: true}
  );

  return parsed;
});

async function seedWizardFromObligation(userId: string, obligationId: string) {
  const obligationRef = firestore.collection("users").doc(userId).collection("reporting").doc(obligationId);
  const obligationSnapshot = await obligationRef.get();
  if (!obligationSnapshot.exists) {
    throw new HttpsError("not-found", "Reporting obligation not found.");
  }

  const obligation = obligationSnapshot.data() || {};
  const existingWizardId = asOptionalString(obligation.wizardId);
  if (existingWizardId) {
    return {wizardId: existingWizardId, obligationId};
  }

  const userProfile = await firestore.collection("users").doc(userId).get();
  const optRegime = normalizeWizardOptRegime(userProfile.data()?.optType);
  const eventType = normalizeWizardEventType(obligation.sourceEventType || obligation.eventType);
  const relatedEmploymentId = asOptionalString(obligation.relatedEmploymentId) || "";
  const employmentData = relatedEmploymentId ? await loadEmploymentData(userId, relatedEmploymentId) : null;
  const eventDate = asOptionalNumber(obligation.eventDate) || Date.now();
  const dueDate = asOptionalNumber(obligation.dueDate) || (eventDate + 10 * DAY_MS);
  const employerNameFromDescription = inferEmployerNameFromDescription(asOptionalString(obligation.description));
  const obligationDescription = buildObligationDescription(
    eventType,
    asOptionalString(employmentData?.employerName) || employerNameFromDescription
  );
  const wizardId = randomUUID();
  const wizardRef = firestore.collection("users").doc(userId).collection("reportingWizards").doc(wizardId);
  const wizardRecord = buildWizardRecord({
    eventType,
    optRegime,
    eventDate,
    dueDate,
    obligationId,
    relatedEmploymentId,
    employmentData,
    fallbackEmployerName: employerNameFromDescription,
  });

  await wizardRef.set(wizardRecord);
  await obligationRef.set(
    {
      eventType: mapWizardEventToObligationType(eventType),
      description: obligationDescription,
      eventDate,
      dueDate,
      wizardId,
      relatedEmploymentId,
      actionType: "open_wizard",
      sourceEventType: eventType,
    },
    {merge: true}
  );

  return {wizardId, obligationId};
}

async function createWizardForEvent(
  userId: string,
  eventType: WizardEventType,
  relatedEmploymentId: string,
  eventDate: number
) {
  const profileSnapshot = await firestore.collection("users").doc(userId).get();
  const optRegime = normalizeWizardOptRegime(profileSnapshot.data()?.optType);
  const employmentData = await loadEmploymentData(userId, relatedEmploymentId);
  const dueDate = eventDate + 10 * DAY_MS;
  const wizardId = randomUUID();
  const obligationId = randomUUID();
  const wizardRef = firestore.collection("users").doc(userId).collection("reportingWizards").doc(wizardId);
  const obligationRef = firestore.collection("users").doc(userId).collection("reporting").doc(obligationId);
  const wizardRecord = buildWizardRecord({
    eventType,
    optRegime,
    eventDate,
    dueDate,
    obligationId,
    relatedEmploymentId,
    employmentData,
  });

  await Promise.all([
    wizardRef.set(wizardRecord),
    obligationRef.set({
      eventType: mapWizardEventToObligationType(eventType),
      description: buildObligationDescription(eventType, asOptionalString(employmentData?.employerName) || ""),
      eventDate,
      dueDate,
      isCompleted: false,
      createdBy: "AUTO",
      wizardId,
      relatedEmploymentId,
      actionType: "open_wizard",
      sourceEventType: eventType,
    }),
  ]);

  return {wizardId, obligationId};
}

function buildWizardRecord(input: {
  eventType: WizardEventType;
  optRegime: WizardOptRegime;
  eventDate: number;
  dueDate: number;
  obligationId: string;
  relatedEmploymentId: string;
  employmentData: Record<string, unknown> | null;
  fallbackEmployerName?: string;
}): ReportingWizardRecord {
  const employment = input.employmentData || {};
  const userInputs: ReportingWizardInput = {
    employerName: asOptionalString(employment.employerName) || input.fallbackEmployerName || "",
    jobTitle: asOptionalString(employment.jobTitle) || "",
    hoursPerWeek: asOptionalNumber(employment.hoursPerWeek),
  };

  return {
    eventType: input.eventType,
    optRegime: input.optRegime,
    status: "ready",
    eventDate: input.eventDate,
    dueDate: input.dueDate,
    obligationId: input.obligationId,
    relatedEmploymentId: input.relatedEmploymentId,
    userInputs,
    generatedChecklist: buildReportingChecklist(input.eventType, input.optRegime),
    generatedDraft: null,
    editedDraft: "",
    policyVersion: POLICY_VERSION,
    policyLastReviewedAt: POLICY_LAST_REVIEWED_AT,
    generatedAt: null,
    copiedAt: null,
    completedAt: null,
  };
}

export function buildReportingChecklist(
  eventType: WizardEventType,
  optRegime: WizardOptRegime
): ChecklistItem[] {
  if (optRegime === "stem") {
    return buildStemChecklist(eventType);
  }
  return buildPostCompletionChecklist(eventType);
}

function buildPostCompletionChecklist(eventType: WizardEventType): ChecklistItem[] {
  const sharedPortalItem: ChecklistItem = {
    actor: "student",
    title: "Update the SEVP Portal within 10 days",
    details: "Use the SEVP Portal to report this employment change and review every field for accuracy before submitting.",
    sourceLabel: POST_COMPLETION_SOURCE_LABEL,
    sourceUrl: POST_COMPLETION_SOURCE_URL,
  };

  const portalFieldsItem: ChecklistItem = {
    actor: "student",
    title: "Prepare the employment details required by the portal",
    details: "Have the employer name, start or end date, worksite address, job title, employment type, and relationship-to-major explanation ready.",
    sourceLabel: PORTAL_GUIDE_SOURCE_LABEL,
    sourceUrl: PORTAL_GUIDE_SOURCE_URL,
  };

  switch (eventType) {
  case "new_employer":
    return [
      sharedPortalItem,
      portalFieldsItem,
      {
        actor: "student",
        title: "Review your relationship-to-major explanation",
        details: "The portal asks you to explain how this job is directly related to your major field of study. Use the draft below as a starting point, then edit it before submitting.",
        sourceLabel: MAJOR_RELATIONSHIP_SOURCE_LABEL,
        sourceUrl: MAJOR_RELATIONSHIP_SOURCE_URL,
      },
    ];
  case "employment_ended":
    return [
      sharedPortalItem,
      {
        actor: "student",
        title: "Enter the employment end date accurately",
        details: "Report the exact date the employment ended so your SEVIS record and unemployment count stay accurate.",
        sourceLabel: POST_COMPLETION_SOURCE_LABEL,
        sourceUrl: POST_COMPLETION_SOURCE_URL,
      },
    ];
  case "material_change":
    return [
      sharedPortalItem,
      portalFieldsItem,
      {
        actor: "student",
        title: "Review whether the change affects your major-relationship explanation",
        details: "If your job duties changed, update the explanation so it still clearly connects the work to your major.",
        sourceLabel: MAJOR_RELATIONSHIP_SOURCE_LABEL,
        sourceUrl: MAJOR_RELATIONSHIP_SOURCE_URL,
      },
    ];
  }
}

function buildStemChecklist(eventType: WizardEventType): ChecklistItem[] {
  switch (eventType) {
  case "new_employer":
    return [
      {
        actor: "student",
        title: "Contact your DSO before relying on this employer for STEM OPT",
        details: "For STEM OPT, you cannot self-add a new employer in the SEVP Portal until your school updates SEVIS after reviewing the training plan.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
      {
        actor: "student",
        title: "Complete a new Form I-983 with the employer",
        details: "Work with the employer to complete a new Form I-983 training plan and submit it to your DSO.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
      {
        actor: "dso",
        title: "Wait for SEVIS to be updated by the school",
        details: "Your DSO must review the training plan and update your SEVIS record before the employer should appear as your STEM OPT employer.",
        sourceLabel: PORTAL_GUIDE_SOURCE_LABEL,
        sourceUrl: PORTAL_GUIDE_SOURCE_URL,
      },
    ];
  case "employment_ended":
    return [
      {
        actor: "student",
        title: "Report the employment end to your DSO within 10 days",
        details: "Tell your DSO when the STEM employer ended so they can update SEVIS appropriately.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
      {
        actor: "student",
        title: "Complete the final evaluation on Form I-983",
        details: "STEM OPT employment endings require the final self-evaluation section of Form I-983.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
    ];
  case "material_change":
    return [
      {
        actor: "student",
        title: "Report the material change to your DSO within 10 days",
        details: "Material changes on STEM OPT should be reported to your DSO instead of being handled only in the portal.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
      {
        actor: "student",
        title: "Update Form I-983 if the training plan changed",
        details: "Changes to duties, compensation, worksite, supervision, hours, or employer structure can require an updated Form I-983.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
      {
        actor: "dso",
        title: "Confirm how your school wants the change submitted",
        details: "Schools vary in how they collect STEM material-change reports, so follow your DSO's process.",
        sourceLabel: STEM_SOURCE_LABEL,
        sourceUrl: STEM_SOURCE_URL,
      },
    ];
  }
}

async function loadEmploymentData(userId: string, employmentId: string): Promise<Record<string, unknown> | null> {
  const snapshot = await firestore.collection("users").doc(userId).collection("employment").doc(employmentId).get();
  if (!snapshot.exists) {
    return null;
  }
  return snapshot.data() || null;
}

async function loadReportingDocumentContext(
  userId: string,
  selectedDocumentIds: string[],
  profile: Record<string, unknown>
) {
  const fallbackIds = Array.isArray(profile.onboardingDocumentIds) ?
    profile.onboardingDocumentIds.filter((value: unknown): value is string => typeof value === "string") :
    [];
  const requestedIds = Array.from(new Set(selectedDocumentIds.length > 0 ? selectedDocumentIds : fallbackIds));
  if (requestedIds.length === 0) {
    return "";
  }

  const snapshots = await Promise.all(
    requestedIds.map((documentId) =>
      firestore.collection("users").doc(userId).collection("documents").doc(documentId).get()
    )
  );

  const usableDocs: ReportingDocumentRecord[] = snapshots
    .filter((snapshot) => snapshot.exists)
    .map((snapshot) => ({
      id: snapshot.id,
      ...((snapshot.data() || {}) as Record<string, unknown>),
    }) as ReportingDocumentRecord)
    .filter((doc) => doc.processingMode !== "storage_only" && doc.processingStatus === "processed");

  return usableDocs.map((doc) => {
    const extractedData = doc.extractedData && typeof doc.extractedData === "object" ?
      JSON.stringify(doc.extractedData) :
      "{}";
    return [
      `Document ID: ${doc.id}`,
      `Type: ${typeof doc.documentType === "string" ? doc.documentType : "Unknown"}`,
      `Summary: ${typeof doc.summary === "string" ? doc.summary : "No summary available."}`,
      `Extracted Data: ${extractedData}`,
    ].join("\n");
  }).join("\n\n");
}

function buildDraftPrompt(input: {
  eventType: WizardEventType;
  optRegime: WizardOptRegime;
  majorName: string;
  userInputs: ReportingWizardInput;
  documentContext: string;
}): string {
  return `
You are helping an F-1 OPT student draft a relationship-to-major paragraph for SEVP reporting.

This is draft assistance only. Do not give legal advice or guarantee compliance.
If the connection is weak or unsupported, return classification "consult_dso_attorney".

Official rule summary:
- The student must explain how the job is directly related to the student's major field of study.
- The explanation should focus on concrete duties, skills, tools, coursework, and subject-matter knowledge.
- Source: ${MAJOR_RELATIONSHIP_SOURCE_LABEL} (${MAJOR_RELATIONSHIP_SOURCE_URL})

Student context:
- OPT regime: ${input.optRegime}
- Reporting event: ${input.eventType}
- Major: ${input.majorName}
- Employer: ${input.userInputs.employerName || "Unknown"}
- Job title: ${input.userInputs.jobTitle || "Unknown"}
- Job duties: ${input.userInputs.jobDuties || "Unknown"}
- Tools/skills: ${input.userInputs.toolsAndSkills || "Not provided"}
- Extra notes: ${input.userInputs.userExplanationNotes || "Not provided"}

Document context:
${input.documentContext || "(No analyzed document context selected.)"}

Return JSON only. The paragraph should be concise, specific, and ready for the student to edit before pasting elsewhere.
  `.trim();
}

function normalizeDraftResult(raw: Record<string, unknown>, warnings: string[]): DraftResult {
  const classification = raw.classification === "consult_dso_attorney" ?
    "consult_dso_attorney" :
    "draft_assistance";
  const confidence = raw.confidence === "high" || raw.confidence === "medium" ? raw.confidence : "low";
  const draftParagraph = asOptionalString(raw.draftParagraph) || "";
  const whyThisDraftFits = asStringList(raw.whyThisDraftFits);
  const missingInputs = asStringList(raw.missingInputs);
  const resultWarnings = [...asStringList(raw.warnings), ...warnings];

  if (!draftParagraph.trim()) {
    return {
      classification: "consult_dso_attorney",
      confidence: confidence as DraftConfidence,
      draftParagraph: "",
      whyThisDraftFits,
      missingInputs,
      warnings: Array.from(new Set(resultWarnings.concat("The draft needs review by your DSO or an immigration attorney before you rely on it."))),
    };
  }

  return {
    classification: classification as DraftClassification,
    confidence: confidence as DraftConfidence,
    draftParagraph,
    whyThisDraftFits,
    missingInputs,
    warnings: Array.from(new Set(resultWarnings)),
  };
}

function buildDraftWarnings(optRegime: WizardOptRegime, hoursPerWeek: number | null): string[] {
  if (hoursPerWeek == null || hoursPerWeek >= 20) {
    return [];
  }
  if (optRegime === "stem") {
    return ["STEM OPT employment generally needs to remain at least 20 hours per week per employer."];
  }
  return ["Jobs below 20 hours per week may not stop the unemployment clock unless you have other qualifying OPT employment."];
}

function inferEmployerNameFromDescription(description: string | null): string {
  if (!description) {
    return "";
  }
  const match = description.match(/:\s*(.+)$/);
  return match?.[1]?.trim() || "";
}

function buildObligationDescription(eventType: WizardEventType, employerName: string): string {
  const label = employerName.trim();
  switch (eventType) {
  case "new_employer":
    return label ? `Report new employer: ${label}` : "Report new employer";
  case "employment_ended":
    return label ? `Report employment ended: ${label}` : "Report employment ended";
  case "material_change":
    return label ? `Report material change: ${label}` : "Report material change";
  }
}

function mapWizardEventToObligationType(eventType: WizardEventType): string {
  switch (eventType) {
  case "new_employer":
    return "NEW_EMPLOYER";
  case "employment_ended":
    return "EMPLOYER_ENDED";
  case "material_change":
    return "EMPLOYMENT_UPDATED";
  }
}

function normalizeWizardEventType(value: unknown): WizardEventType {
  if (value === "new_employer" || value === "employment_ended" || value === "material_change") {
    return value;
  }
  if (value === "NEW_EMPLOYER") return "new_employer";
  if (value === "EMPLOYER_ENDED") return "employment_ended";
  if (value === "EMPLOYMENT_UPDATED") return "material_change";
  throw new HttpsError("invalid-argument", "Unsupported reporting wizard event type.");
}

function normalizeWizardOptRegime(value: unknown): WizardOptRegime {
  return typeof value === "string" && value.toLowerCase() === "stem" ? "stem" : "post_completion";
}

function toWizardInput(value: unknown): ReportingWizardInput {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as ReportingWizardInput;
}

function parseJsonRecord(value: string): Record<string, unknown> {
  return JSON.parse(value) as Record<string, unknown>;
}

function asOptionalString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed || null;
}

function asOptionalNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string");
}

function asNonEmptyString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new HttpsError("invalid-argument", `${fieldName} is required.`);
  }
  return value.trim();
}

function asNumber(value: unknown, fieldName: string): number {
  if (typeof value !== "number" || Number.isNaN(value)) {
    throw new HttpsError("invalid-argument", `${fieldName} must be a number.`);
  }
  return value;
}
