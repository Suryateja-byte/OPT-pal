import * as logger from "firebase-functions/logger";
import { onCall, HttpsError, onRequest } from "firebase-functions/v2/https";
import { onObjectFinalized } from "firebase-functions/v2/storage";
import * as admin from "firebase-admin";
import { randomUUID } from "crypto";
import { VertexAI } from "@google-cloud/vertexai";
import {
  documentRef,
  extractTextFromDocument,
  firestore,
  loadStoredDocumentBytes,
  sanitizeInlineFileName,
  storagePathForDocument,
  wrapDocumentKey,
} from "./documentSecurity";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const vertexAI = new VertexAI({
  project: process.env.GCLOUD_PROJECT || "opt-pal",
  location: "us-central1",
});
const model = vertexAI.getGenerativeModel({model: "gemini-2.5-flash"});

export const prepareSecureUpload = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  const userId = request.auth.uid;
  const fileName = asNonEmptyString(request.data.fileName, "fileName");
  const userTag = asNonEmptyString(request.data.userTag, "userTag");
  const contentType = asNonEmptyString(request.data.contentType, "contentType");
  const byteSize = asNumber(request.data.byteSize, "byteSize");
  const processingMode = normalizeProcessingMode(request.data.processingMode);
  const processingConsentAcceptedAt =
    processingMode === "analyze"
      ? asNumber(request.data.processingConsentAcceptedAt, "processingConsentAcceptedAt")
      : null;
  const consentProviders = Array.isArray(request.data.consentProviders) ?
    request.data.consentProviders.filter((value: unknown): value is string => typeof value === "string") :
    [];
  const documentCategory = normalizeDocumentCategory(request.data.documentCategory);
  const chatEligible = asOptionalBoolean(request.data.chatEligible) ?? (
    documentCategory !== "tax_sensitive" && processingMode === "analyze"
  );
  const encryptedDocumentKey = asNonEmptyString(request.data.encryptedDocumentKey, "encryptedDocumentKey");

  const documentId = randomUUID();
  const storagePath = storagePathForDocument(userId, documentId);
  const wrappedDocumentKey = await wrapDocumentKey(encryptedDocumentKey);
  const uploadedAt = Date.now();

  await documentRef(userId, documentId).set({
    fileName,
    userTag,
    storagePath,
    downloadUrl: "",
    contentType,
    byteSize,
    encryptionVersion: 1,
    processingMode,
    processingConsentAcceptedAt,
    consentProviders,
    processingStatus: processingMode === "analyze" ? "awaiting_upload" : "stored",
    processingError: "",
    documentType: "",
    documentCategory,
    chatEligible,
    summary: "",
    extractedData: {},
    processedAt: null,
    uploadedAt,
    wrappedDocumentKey,
    hasEncryptedContent: true,
  });

  return {
    documentId,
    storagePath,
    encryptionVersion: 1,
  };
});

export const processDocument = onObjectFinalized({
  bucket: process.env.STORAGE_BUCKET,
  cpu: 2,
  memory: "1GiB",
}, async (event) => {
  const filePath = event.data.name;
  if (!filePath) {
    return;
  }

  const pathParts = filePath.split("/");
  if (pathParts.length !== 4 || pathParts[0] !== "users" || pathParts[2] !== "documents") {
    return;
  }

  const userId = pathParts[1];
  const fileName = pathParts[3];
  const documentId = fileName.replace(/\.enc$/i, "");
  const docRef = documentRef(userId, documentId);
  const snapshot = await docRef.get();

  if (!snapshot.exists) {
    logger.warn("Metadata missing for secure upload.", {userId, filePath});
    return;
  }

  const metadata = snapshot.data() || {};
  if (metadata.processingMode === "storage_only") {
    await docRef.update({
      processingStatus: "stored",
      processingError: "",
    });
    return;
  }

  try {
    const plainBytes = await loadStoredDocumentBytes(
      userId,
      documentId,
      metadata.storagePath || filePath,
      metadata.wrappedDocumentKey
    );
    const contentType = typeof metadata.contentType === "string" ? metadata.contentType : "";
    const fullText = await extractTextFromDocument(plainBytes, contentType);

    if (!fullText) {
      await docRef.update({
        extractedData: {error: "No text detected"},
        processingStatus: "error",
        processingError: "No text detected",
        processedAt: Date.now(),
      });
      return;
    }

    const structuredData = await structureDataWithLLM(fullText);
    const updateData: Record<string, unknown> = {
      extractedData: structuredData.data,
      documentType: structuredData.documentType,
      summary: structuredData.summary,
      processingStatus: "processed",
      processingError: "",
      processedAt: Date.now(),
    };

    if (structuredData.documentType &&
      structuredData.documentType !== "Unknown" &&
      structuredData.documentType !== "unknown") {
      updateData.userTag = structuredData.documentType;
    }
    if (structuredData.documentType === "W-2 Form") {
      updateData.documentCategory = "tax_sensitive";
      updateData.chatEligible = false;
    }

    await docRef.update(updateData);
  } catch (error) {
    logger.error("Error processing secure document", error);
    await docRef.update({
      extractedData: {
        error: error instanceof Error ? error.message : "Unknown error",
      },
      processingStatus: "error",
      processingError: error instanceof Error ? error.message : "Unknown error",
      processedAt: Date.now(),
    });
  }
});

export const documentContent = onRequest(async (request, response) => {
  if (request.method !== "GET") {
    response.status(405).send("Method not allowed.");
    return;
  }

  try {
    const idToken = parseBearerToken(request.header("Authorization"));
    const appCheckToken = request.header("X-Firebase-AppCheck");
    if (!idToken || !appCheckToken) {
      response.status(401).send("Missing authentication.");
      return;
    }

    const [decodedToken] = await Promise.all([
      admin.auth().verifyIdToken(idToken),
      admin.appCheck().verifyToken(appCheckToken),
    ]);

    const documentId = typeof request.query.documentId === "string" ? request.query.documentId : "";
    if (!documentId) {
      response.status(400).send("Missing documentId.");
      return;
    }

    const snapshot = await documentRef(decodedToken.uid, documentId).get();
    if (!snapshot.exists) {
      response.status(404).send("Document not found.");
      return;
    }

    const metadata = snapshot.data() || {};
    const bytes = await loadStoredDocumentBytes(
      decodedToken.uid,
      documentId,
      metadata.storagePath || "",
      metadata.wrappedDocumentKey
    );

    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Content-Type", metadata.contentType || "application/octet-stream");
    response.setHeader(
      "Content-Disposition",
      `inline; filename="${sanitizeInlineFileName(metadata.fileName || documentId)}"`
    );
    response.status(200).send(bytes);
  } catch (error) {
    logger.error("Secure content request failed", error);
    response.status(401).send("Unable to verify request.");
  }
});

export const reprocessDocuments = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  const userId = request.auth.uid;
  const snapshot = await firestore.collection("users").doc(userId).collection("documents").get();
  let processedCount = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (data.processingMode === "storage_only") {
      continue;
    }

    if (!data.storagePath) {
      continue;
    }

    try {
      const plainBytes = await loadStoredDocumentBytes(
        userId,
        doc.id,
        data.storagePath,
        data.wrappedDocumentKey
      );
      const fullText = await extractTextFromDocument(plainBytes, data.contentType || "");
      if (!fullText) {
        continue;
      }

      const structuredData = await structureDataWithLLM(fullText);
      const updateData: Record<string, unknown> = {
        extractedData: structuredData.data,
        documentType: structuredData.documentType,
        summary: structuredData.summary,
        processingStatus: "processed",
        processingError: "",
        processedAt: Date.now(),
      };
      if (structuredData.documentType === "W-2 Form") {
        updateData.documentCategory = "tax_sensitive";
        updateData.chatEligible = false;
      }
      await doc.ref.update(updateData);
      processedCount++;
    } catch (error) {
      logger.error("Failed to reprocess secure document", {documentId: doc.id, error});
    }
  }

  return {message: `Reprocessed ${processedCount} documents.`};
});

export const getTravelAdvisorPolicyBundle = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  return buildTravelAdvisorPolicyBundle();
});

async function structureDataWithLLM(text: string): Promise<{ documentType: string; data: unknown; summary: string }> {
  const prompt = `
You are an expert document parsing API. Respond only in valid JSON.

Analyze the OCR text below and return:
1. "document_type": specific document type.
2. "summary": one concise sentence.
3. "data": key-value pairs with the most useful fields in snake_case.

If the document is an I-20, an EAD card, a passport bio page, or an F-1 visa, include these canonical keys when present:
- sevis_id
- school_name
- cip_code
- opt_type
- opt_start_date
- opt_end_date
- ead_category
- uscis_number
- travel_signature_date
- passport_issuing_country
- passport_expiration_date
- visa_class
- visa_expiration_date

If the document is a prior I-983, offer letter, or job description, also include these canonical keys when present:
- degree_level
- degree_awarded_date
- employer_ein
- employer_naics
- hours_per_week
- compensation_text
- site_name
- site_address
- employer_official_name
- employer_official_title
- employer_official_email
- employer_official_phone
- i983_role_description
- i983_goals_objectives
- i983_employer_oversight
- i983_measures_assessments
- i983_additional_remarks

Use YYYY-MM-DD for dates when possible. Keep any other useful fields too.

OCR Input Text:
${text}
  `;

  try {
    const result = await model.generateContent(prompt);
    const extractedText = result.response.candidates?.[0]?.content?.parts?.[0]?.text || "{}";
    const jsonString = extractedText.replace(/```json/g, "").replace(/```/g, "").trim();
    const parsed = JSON.parse(jsonString);
    const normalized = canonicalizeDocumentExtraction(parsed.document_type, parsed.data, text);
    return {
      documentType: normalized.documentType,
      data: normalized.data,
      summary: parsed.summary || "",
    };
  } catch (error) {
    logger.error("Failed to structure document text", error);
    return {
      documentType: "Unknown",
      data: {raw_text: text, error: "Failed to structure data"},
      summary: "Document processed but structure failed.",
    };
  }
}

export function canonicalizeDocumentExtraction(
  rawDocumentType: unknown,
  rawData: unknown,
  rawText: string
): { documentType: string; data: Record<string, unknown> } {
  const data = toRecord(rawData);
  const normalizedType = normalizeStructuredDocumentType(rawDocumentType, data, rawText);
  const normalizedIndex = buildNormalizedIndex(data);
  const canonicalData: Record<string, unknown> = normalizedType === "W-2 Form" ? {} : {...data};

  const sevisId = firstString(normalizedIndex, "sevis_id", "student_sevis_id");
  const schoolName = firstString(
    normalizedIndex,
    "school_name",
    "university_name",
    "school",
    "university"
  );
  const cipCode = firstString(
    normalizedIndex,
    "cip_code",
    "major_code",
    "program_code"
  );
  const majorName = firstString(
    normalizedIndex,
    "major_name",
    "major",
    "program_name",
    "major_name_1",
    "major1"
  );
  const eadCategory = firstString(
    normalizedIndex,
    "ead_category",
    "category",
    "card_category"
  )?.toUpperCase() || null;
  const optType = normalizeOptType(
    firstString(normalizedIndex, "opt_type", "opt_status"),
    eadCategory,
    rawText
  );
  const optStartDate = normalizeDateValue(firstValue(
    normalizedIndex,
    "opt_start_date",
    "ead_start_date",
    "start_date",
    "valid_from",
    "employment_start_date"
  ));
  const optEndDate = normalizeDateValue(firstValue(
    normalizedIndex,
    "opt_end_date",
    "ead_end_date",
    "end_date",
    "valid_to",
    "employment_end_date"
  ));
  const uscisNumber = firstString(
    normalizedIndex,
    "uscis_number",
    "a_number",
    "alien_number",
    "ead_number"
  );
  const travelSignatureDate = normalizeDateValue(firstValue(
    normalizedIndex,
    "travel_signature_date",
    "travel_endorsement_date",
    "travel_signature",
    "travel_authorization_date"
  ));
  const passportIssuingCountry = firstString(
    normalizedIndex,
    "passport_issuing_country",
    "issuing_country",
    "country_of_issuance",
    "nationality"
  );
  const passportExpirationDate = normalizeDateValue(firstValue(
    normalizedIndex,
    "passport_expiration_date",
    "expiration_date",
    "expiry_date",
    "date_of_expiration",
    "passport_expires"
  ));
  const visaClass = normalizeVisaClass(firstString(
    normalizedIndex,
    "visa_class",
    "visa_type",
    "class",
    "type"
  ));
  const visaExpirationDate = normalizeDateValue(firstValue(
    normalizedIndex,
    "visa_expiration_date",
    "expiration_date",
    "expiry_date",
    "visa_expires",
    "expires_on"
  ));
  const degreeLevel = firstString(normalizedIndex, "degree_level", "level_type_of_qualifying_degree");
  const degreeAwardedDate = normalizeDateValue(firstValue(normalizedIndex, "degree_awarded_date", "date_awarded"));
  const employerEin = firstString(normalizedIndex, "employer_ein", "ein");
  const employerNaics = firstString(normalizedIndex, "employer_naics", "naics_code");
  const hoursPerWeek = firstString(normalizedIndex, "hours_per_week", "weekly_hours");
  const compensationText = firstString(normalizedIndex, "compensation_text", "salary_amount_and_frequency", "salary");
  const siteName = firstString(normalizedIndex, "site_name");
  const siteAddress = firstString(normalizedIndex, "site_address", "worksite_address");
  const employerOfficialName = firstString(normalizedIndex, "employer_official_name", "official_name");
  const employerOfficialTitle = firstString(normalizedIndex, "employer_official_title", "official_title");
  const employerOfficialEmail = firstString(normalizedIndex, "employer_official_email", "official_email");
  const employerOfficialPhone = firstString(normalizedIndex, "employer_official_phone", "official_phone");
  const i983RoleDescription = firstString(normalizedIndex, "i983_role_description", "student_role");
  const i983GoalsObjectives = firstString(normalizedIndex, "i983_goals_objectives", "goals_and_objectives");
  const i983EmployerOversight = firstString(normalizedIndex, "i983_employer_oversight", "employer_oversight");
  const i983MeasuresAssessments = firstString(normalizedIndex, "i983_measures_assessments", "measures_and_assessments");
  const i983AdditionalRemarks = firstString(normalizedIndex, "i983_additional_remarks", "additional_remarks");

  if (sevisId) {
    canonicalData.sevis_id = sevisId.toUpperCase();
  }
  if (schoolName) {
    canonicalData.school_name = schoolName;
  }
  if (cipCode) {
    canonicalData.cip_code = cipCode;
  }
  if (majorName) {
    canonicalData.major_name = majorName;
  }
  if (eadCategory) {
    canonicalData.ead_category = eadCategory;
  }
  if (optType) {
    canonicalData.opt_type = optType;
  }
  if (optStartDate) {
    canonicalData.opt_start_date = optStartDate;
  }
  if (optEndDate) {
    canonicalData.opt_end_date = optEndDate;
  }
  if (uscisNumber) {
    canonicalData.uscis_number = uscisNumber;
  }
  if (travelSignatureDate) {
    canonicalData.travel_signature_date = travelSignatureDate;
  }
  if (passportIssuingCountry) {
    canonicalData.passport_issuing_country = passportIssuingCountry;
  }
  if (passportExpirationDate) {
    canonicalData.passport_expiration_date = passportExpirationDate;
  }
  if (visaClass) {
    canonicalData.visa_class = visaClass;
  }
  if (visaExpirationDate) {
    canonicalData.visa_expiration_date = visaExpirationDate;
  }
  if (normalizedType !== "W-2 Form") {
    if (degreeLevel) canonicalData.degree_level = degreeLevel;
    if (degreeAwardedDate) canonicalData.degree_awarded_date = degreeAwardedDate;
    if (employerEin) canonicalData.employer_ein = employerEin;
    if (employerNaics) canonicalData.employer_naics = employerNaics;
    if (hoursPerWeek) canonicalData.hours_per_week = hoursPerWeek;
    if (compensationText) canonicalData.compensation_text = compensationText;
    if (siteName) canonicalData.site_name = siteName;
    if (siteAddress) canonicalData.site_address = siteAddress;
    if (employerOfficialName) canonicalData.employer_official_name = employerOfficialName;
    if (employerOfficialTitle) canonicalData.employer_official_title = employerOfficialTitle;
    if (employerOfficialEmail) canonicalData.employer_official_email = employerOfficialEmail;
    if (employerOfficialPhone) canonicalData.employer_official_phone = employerOfficialPhone;
    if (i983RoleDescription) canonicalData.i983_role_description = i983RoleDescription;
    if (i983GoalsObjectives) canonicalData.i983_goals_objectives = i983GoalsObjectives;
    if (i983EmployerOversight) canonicalData.i983_employer_oversight = i983EmployerOversight;
    if (i983MeasuresAssessments) canonicalData.i983_measures_assessments = i983MeasuresAssessments;
    if (i983AdditionalRemarks) canonicalData.i983_additional_remarks = i983AdditionalRemarks;
  }

  if (normalizedType === "I-20 Form") {
    canonicalData.document_type = "i20";
  } else if (normalizedType === "EAD Card") {
    canonicalData.document_type = "ead";
  } else if (normalizedType === "Passport Bio Page") {
    canonicalData.document_type = "passport";
  } else if (normalizedType === "F-1 Visa") {
    canonicalData.document_type = "visa";
  } else if (normalizedType === "I-983 Form") {
    canonicalData.document_type = "i983";
  } else if (normalizedType === "Offer Letter") {
    canonicalData.document_type = "offer_letter";
  } else if (normalizedType === "Job Description") {
    canonicalData.document_type = "job_description";
  } else if (normalizedType === "W-2 Form") {
    canonicalData.document_type = "w2";
    const employeeName = firstString(normalizedIndex, "employee_name", "employee", "name");
    const employeeSsnLast4 = normalizeSsnLast4(firstString(
      normalizedIndex,
      "employee_ssn_last4",
      "employee_ssn",
      "ssn"
    ));
    const employerName = firstString(normalizedIndex, "employer_name", "employer");
    const employerEinMasked = normalizeMaskedEin(firstString(
      normalizedIndex,
      "employer_ein_masked",
      "employer_ein",
      "ein"
    ));
    const taxYear = normalizeTaxYear(firstValue(normalizedIndex, "tax_year", "year", "w2_year"));
    const wagesBox1 = normalizeMoneyValue(firstValue(normalizedIndex, "wages_box1", "box_1", "box1", "wages"));
    const federalWithholdingBox2 = normalizeMoneyValue(firstValue(normalizedIndex, "federal_withholding_box2", "box_2", "box2"));
    const socialSecurityWagesBox3 = normalizeMoneyValue(firstValue(normalizedIndex, "social_security_wages_box3", "box_3", "box3"));
    const socialSecurityTaxBox4 = normalizeMoneyValue(firstValue(normalizedIndex, "social_security_tax_box4", "box_4", "box4"));
    const medicareWagesBox5 = normalizeMoneyValue(firstValue(normalizedIndex, "medicare_wages_box5", "box_5", "box5"));
    const medicareTaxBox6 = normalizeMoneyValue(firstValue(normalizedIndex, "medicare_tax_box6", "box_6", "box6"));
    const stateRows = normalizeStateWageRows(firstValue(normalizedIndex, "state_wage_rows", "state_rows"));

    if (taxYear !== null) canonicalData.tax_year = taxYear;
    if (employeeName) canonicalData.employee_name = employeeName;
    if (employeeSsnLast4) canonicalData.employee_ssn_last4 = employeeSsnLast4;
    if (employerName) canonicalData.employer_name = employerName;
    if (employerEinMasked) canonicalData.employer_ein_masked = employerEinMasked;
    if (wagesBox1 !== null) canonicalData.wages_box1 = wagesBox1;
    if (federalWithholdingBox2 !== null) canonicalData.federal_withholding_box2 = federalWithholdingBox2;
    if (socialSecurityWagesBox3 !== null) canonicalData.social_security_wages_box3 = socialSecurityWagesBox3;
    if (socialSecurityTaxBox4 !== null) canonicalData.social_security_tax_box4 = socialSecurityTaxBox4;
    if (medicareWagesBox5 !== null) canonicalData.medicare_wages_box5 = medicareWagesBox5;
    if (medicareTaxBox6 !== null) canonicalData.medicare_tax_box6 = medicareTaxBox6;
    if (stateRows.length > 0) canonicalData.state_wage_rows = stateRows;
  }

  return {
    documentType: normalizedType,
    data: canonicalData,
  };
}

function normalizeStructuredDocumentType(
  rawDocumentType: unknown,
  data: Record<string, unknown>,
  rawText: string
): string {
  const typeLabel = typeof rawDocumentType === "string" ? rawDocumentType : "";
  const normalizedType = normalizeLooseLabel(typeLabel);
  const normalizedText = normalizeLooseLabel(rawText);
  const normalizedIndex = buildNormalizedIndex(data);

  if (
    normalizedType.includes("passport") ||
    normalizedText.includes("passport") ||
    normalizedIndex.has("passportissuingcountry") ||
    (normalizedIndex.has("nationality") && normalizedIndex.has("dateofbirth"))
  ) {
    return "Passport Bio Page";
  }

  if (
    normalizedType.includes("visa") ||
    normalizedText.includes("nonimmigrantvisa") ||
    normalizedText.includes("visaclass") ||
    normalizedIndex.has("visaclass")
  ) {
    return "F-1 Visa";
  }

  if (
    normalizedType.includes("i20") ||
    normalizedText.includes("i20") ||
    normalizedIndex.has("sevisid")
  ) {
    return "I-20 Form";
  }

  if (
    normalizedType.includes("ead") ||
    normalizedType.includes("employmentauthorization") ||
    normalizedText.includes("employmentauthorization") ||
    normalizedIndex.has("eadcategory") ||
    normalizedIndex.has("category")
  ) {
    return "EAD Card";
  }

  if (
    normalizedType.includes("w2") ||
    normalizedType.includes("wageandtaxstatement") ||
    normalizedText.includes("wageandtaxstatement") ||
    normalizedIndex.has("socialsecuritytaxbox4") ||
    normalizedIndex.has("medicaretaxbox6")
  ) {
    return "W-2 Form";
  }

  if (
    normalizedType.includes("i983") ||
    normalizedText.includes("trainingplanforstemoptstudents") ||
    normalizedIndex.has("i983roledescription") ||
    normalizedIndex.has("degreelevel")
  ) {
    return "I-983 Form";
  }

  if (
    normalizedType.includes("offerletter") ||
    normalizedText.includes("offerletter")
  ) {
    return "Offer Letter";
  }

  if (
    normalizedType.includes("jobdescription") ||
    normalizedText.includes("jobdescription")
  ) {
    return "Job Description";
  }

  return typeLabel || "Unknown";
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

function normalizeOptType(value: string | null, eadCategory: string | null, rawText: string): string | null {
  const normalizedValue = normalizeLooseLabel(value || "");
  const normalizedCategory = normalizeLooseLabel(eadCategory || "");
  const normalizedText = normalizeLooseLabel(rawText);

  if (
    normalizedValue.includes("stem") ||
    normalizedCategory.includes("c03c") ||
    normalizedCategory.includes("c3c") ||
    normalizedText.includes("stemopt")
  ) {
    return "stem";
  }

  if (
    normalizedValue.includes("initial") ||
    normalizedValue.includes("postcompletion") ||
    normalizedCategory.includes("c03a") ||
    normalizedCategory.includes("c3a") ||
    normalizedCategory.includes("c03b") ||
    normalizedCategory.includes("c3b")
  ) {
    return "initial";
  }

  return null;
}

function normalizeDateValue(value: unknown): string | null {
  if (value === undefined || value === null) {
    return null;
  }

  if (typeof value === "number" && Number.isFinite(value)) {
    const millis = value > 9999999999 ? value : value * 1000;
    return new Date(millis).toISOString().slice(0, 10);
  }

  const raw = `${value}`.trim();
  if (!raw) {
    return null;
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return raw;
  }

  const slashDate = raw.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (slashDate) {
    const [, month, day, year] = slashDate;
    return `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
  }

  const yearFirstSlashDate = raw.match(/^(\d{4})\/(\d{1,2})\/(\d{1,2})$/);
  if (yearFirstSlashDate) {
    const [, year, month, day] = yearFirstSlashDate;
    return `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
  }

  const monthNameDate = raw.match(/^([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})$/);
  if (monthNameDate) {
    const [, monthName, day, year] = monthNameDate;
    const month = MONTH_LOOKUP[monthName.toLowerCase()];
    if (month) {
      return `${year}-${month}-${day.padStart(2, "0")}`;
    }
  }

  const numeric = Number(raw);
  if (Number.isFinite(numeric)) {
    const millis = numeric > 9999999999 ? numeric : numeric * 1000;
    return new Date(millis).toISOString().slice(0, 10);
  }

  return null;
}

function normalizeLooseLabel(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function normalizeTaxYear(value: unknown): number | null {
  if (typeof value === "number" && Number.isInteger(value)) {
    return value;
  }
  const raw = `${value || ""}`.trim();
  if (!/^\d{4}$/.test(raw)) {
    return null;
  }
  return Number(raw);
}

function normalizeMoneyValue(value: unknown): number | null {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return Math.round(value * 100) / 100;
  }
  const parsed = Number(`${value}`.replace(/[$,\s]/g, ""));
  return Number.isFinite(parsed) ? Math.round(parsed * 100) / 100 : null;
}

function normalizeSsnLast4(value: string | null): string | null {
  const digits = `${value || ""}`.replace(/\D/g, "");
  if (digits.length < 4) {
    return null;
  }
  return digits.slice(-4);
}

function normalizeMaskedEin(value: string | null): string | null {
  const digits = `${value || ""}`.replace(/\D/g, "");
  if (digits.length < 9) {
    return null;
  }
  return `XX-XXX${digits.slice(-4)}`;
}

function normalizeStateWageRows(value: unknown): Array<Record<string, unknown>> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => {
    const row = toRecord(item);
    return {
      stateCode: typeof row.stateCode === "string" ? row.stateCode : row.state_code,
      wages: normalizeMoneyValue(row.wages),
      withholding: normalizeMoneyValue(row.withholding),
    };
  });
}

function normalizeVisaClass(value: string | null): string | null {
  const normalized = `${value || ""}`.trim().toUpperCase();
  if (!normalized) {
    return null;
  }
  if (normalized === "F1") {
    return "F-1";
  }
  return normalized;
}

function toRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

const MONTH_LOOKUP: Record<string, string> = {
  january: "01",
  jan: "01",
  february: "02",
  feb: "02",
  march: "03",
  mar: "03",
  april: "04",
  apr: "04",
  may: "05",
  june: "06",
  jun: "06",
  july: "07",
  jul: "07",
  august: "08",
  aug: "08",
  september: "09",
  sep: "09",
  sept: "09",
  october: "10",
  oct: "10",
  november: "11",
  nov: "11",
  december: "12",
  dec: "12",
};

function parseBearerToken(headerValue?: string): string | null {
  if (!headerValue?.startsWith("Bearer ")) {
    return null;
  }
  return headerValue.substring("Bearer ".length).trim();
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

function asOptionalBoolean(value: unknown): boolean | null {
  return typeof value === "boolean" ? value : null;
}

function normalizeDocumentCategory(value: unknown): "general" | "tax_sensitive" {
  return value === "tax_sensitive" ? "tax_sensitive" : "general";
}

function normalizeProcessingMode(value: unknown): "storage_only" | "analyze" {
  if (value === "storage_only" || value === "analyze") {
    return value;
  }
  throw new HttpsError("invalid-argument", "processingMode must be storage_only or analyze.");
}

function buildTravelAdvisorPolicyBundle() {
  const lastReviewedAt = Date.UTC(2026, 2, 10);
  return {
    version: "2026.03.10",
    generatedAt: Date.now(),
    lastReviewedAt,
    staleAfterDays: 30,
    sources: [
      {
        id: "ice_travel",
        label: "ICE SEVP Travel Guidance",
        url: "https://www.ice.gov/sevis/travel",
        effectiveDate: "2025-12-12",
        lastReviewedDate: "2026-03-10",
        summary: "Core reentry document expectations for F-1 students on OPT and STEM OPT."
      },
      {
        id: "study_travel",
        label: "Study in the States Travel Guidance",
        url: "https://studyinthestates.dhs.gov/students/traveling-as-an-international-student",
        effectiveDate: "2025-12-12",
        lastReviewedDate: "2026-03-10",
        summary: "Travel considerations, I-515A risk, and practical training travel reminders."
      },
      {
        id: "uscis_cap_gap",
        label: "H-1B Modernization / Cap-Gap Context",
        url: "https://public-inspection.federalregister.gov/2024-29354.pdf",
        effectiveDate: "2025-01-17",
        lastReviewedDate: "2026-03-10",
        summary: "Cap-gap timing and related change-of-status sensitivity."
      },
      {
        id: "dos_automatic_revalidation",
        label: "DOS Automatic Revalidation",
        url: "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/automatic-revalidation.html",
        effectiveDate: "2025-12-12",
        lastReviewedDate: "2026-03-10",
        summary: "Rules for limited trips to Canada, Mexico, and adjacent islands without a new visa stamp."
      },
      {
        id: "dos_wait_times",
        label: "DOS Global Visa Wait Times",
        url: "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/global-visa-wait-times.html",
        effectiveDate: "2025-12-12",
        lastReviewedDate: "2026-03-10",
        summary: "Official global nonimmigrant visa appointment wait-time tool."
      },
      {
        id: "dos_nonresident_niv",
        label: "DOS Interview Location Guidance",
        url: "https://travel.state.gov/content/travel/en/us-visas.html",
        effectiveDate: "2025-09-06",
        lastReviewedDate: "2026-03-10",
        summary: "Nonimmigrant visa applicants should interview in their country of nationality or residence."
      },
      {
        id: "dos_country_restrictions",
        label: "PP 10998 / Current Visa-Issuance Restrictions",
        url: "https://www.whitehouse.gov/presidential-actions/2025/12/restricting-and-limiting-the-entry-of-foreign-nationals-to-protect-the-security-of-the-united-states/",
        effectiveDate: "2026-01-01",
        lastReviewedDate: "2026-03-10",
        summary: "Nationality-based visa-issuance restrictions that materially affect new F, M, and J visa paths."
      },
      {
        id: "cbp_passport_validity",
        label: "CBP Six-Month Passport Validity Update",
        url: "https://www.cbp.gov/sites/default/files/assets/documents/2022-Mar/Six-Month%20Passport%20Validity%20Update%2020220316.pdf",
        effectiveDate: "2022-03-16",
        lastReviewedDate: "2026-03-10",
        summary: "Passport-validity exemptions for travelers whose passports are valid through the intended stay."
      },
      {
        id: "stem_pending_secondary",
        label: "NCSU STEM OPT Travel Guidance",
        url: "https://internationalservices.ncsu.edu/student-employment/stem-opt-extension/travel-on-stem-opt/",
        effectiveDate: "2025-12-12",
        lastReviewedDate: "2026-03-10",
        summary: "University guidance for STEM-extension travel while a STEM application is pending."
      },
    ],
    ruleCards: [
      {
        ruleId: "ESCALATION",
        title: "Hard-stop escalation triggers",
        summary: "Cap-gap, stale policy data, and complex legal/status issues are escalated out of the app.",
        citationIds: ["uscis_cap_gap", "ice_travel", "study_travel"]
      },
      {
        ruleId: "GRACE_PERIOD",
        title: "Grace-period reentry",
        summary: "The 60-day grace period is not a travel-and-reentry window.",
        citationIds: ["ice_travel", "study_travel"]
      },
      {
        ruleId: "PASSPORT_VALIDITY",
        title: "Passport validity",
        summary: "Passport must be valid for the intended stay, and often for six months beyond it unless exempt.",
        citationIds: ["cbp_passport_validity"]
      },
      {
        ruleId: "VISA_PATH",
        title: "Visa path",
        summary: "Use a valid F-1 visa, a qualifying automatic-revalidation path, or a new visa process in the right location.",
        citationIds: ["dos_automatic_revalidation", "dos_nonresident_niv", "dos_wait_times"]
      },
      {
        ruleId: "I20_SIGNATURE",
        title: "I-20 travel signature age",
        summary: "OPT travel signatures should be current at the time of reentry.",
        citationIds: ["ice_travel", "study_travel"]
      },
      {
        ruleId: "EAD_STATUS",
        title: "EAD and OPT status",
        summary: "Approved OPT travel generally requires a valid EAD in hand; pending cases and STEM extensions are riskier.",
        citationIds: ["ice_travel", "study_travel", "stem_pending_secondary"]
      },
      {
        ruleId: "EMPLOYMENT_EVIDENCE",
        title: "Employment evidence",
        summary: "Approved OPT/STEM reentry should be backed by current employment or offer proof.",
        citationIds: ["ice_travel"]
      },
      {
        ruleId: "UNEMPLOYMENT_LIMIT",
        title: "Unemployment limit",
        summary: "Travel does not cure a status problem caused by exceeding allowed unemployment time.",
        citationIds: ["ice_travel"]
      },
      {
        ruleId: "COUNTRY_RESTRICTIONS",
        title: "Current visa-issuance overlays",
        summary: "Nationality-based suspensions can block or weaken a new F-1 visa path even if other documents are fine.",
        citationIds: ["dos_country_restrictions", "dos_nonresident_niv"]
      },
    ],
    passportValidity: {
      defaultAdditionalValidityMonths: 6,
      sixMonthClubCountries: SIX_MONTH_CLUB_COUNTRIES,
    },
    automaticRevalidation: {
      allowedCountries: ["Canada", "Mexico"],
      adjacentIslands: AUTOMATIC_REVALIDATION_ADJACENT_ISLANDS,
      excludedCountries: ["Cuba"],
      maxTripLengthDays: 30,
      summary: "Automatic revalidation can apply to certain trips under 30 days that do not involve a new visa application.",
    },
    countryRestrictions: [
      ...FULL_SUSPENSION_COUNTRIES.map((nationality) => ({
        nationality,
        summary: `${nationality} is currently subject to a full or near-full visa-issuance suspension under PP 10998.`,
        visaClasses: ["ALL"],
        appliesToNewVisaOnly: true,
        isFullSuspension: true,
      })),
      ...PARTIAL_FMJ_COUNTRIES.map((nationality) => ({
        nationality,
        summary: `${nationality} is currently subject to a partial visa-issuance suspension that includes F, M, and J visas.`,
        visaClasses: ["F-1", "M-1", "J-1"],
        appliesToNewVisaOnly: true,
        isFullSuspension: false,
      })),
    ],
  };
}

const FULL_SUSPENSION_COUNTRIES = [
  "Afghanistan",
  "Burkina Faso",
  "Burma",
  "Chad",
  "Equatorial Guinea",
  "Eritrea",
  "Haiti",
  "Iran",
  "Laos",
  "Libya",
  "Mali",
  "Niger",
  "Republic of the Congo",
  "Sierra Leone",
  "Somalia",
  "South Sudan",
  "Sudan",
  "Syria",
  "Yemen",
];

const PARTIAL_FMJ_COUNTRIES = [
  "Angola",
  "Antigua and Barbuda",
  "Benin",
  "Burundi",
  "Cote d'Ivoire",
  "Cuba",
  "Dominica",
  "Gabon",
  "Gambia",
  "Malawi",
  "Mauritania",
  "Nigeria",
  "Senegal",
  "Tanzania",
  "Togo",
  "Tonga",
  "Venezuela",
  "Zambia",
  "Zimbabwe",
];

const AUTOMATIC_REVALIDATION_ADJACENT_ISLANDS = [
  "Anguilla",
  "Antigua and Barbuda",
  "Aruba",
  "Bahamas",
  "Barbados",
  "Bermuda",
  "Bonaire",
  "British Virgin Islands",
  "Cayman Islands",
  "Curacao",
  "Dominica",
  "Dominican Republic",
  "Grenada",
  "Guadeloupe",
  "Haiti",
  "Jamaica",
  "Martinique",
  "Montserrat",
  "Saba",
  "Saint Barthelemy",
  "Saint Kitts and Nevis",
  "Saint Lucia",
  "Saint Martin",
  "Saint Pierre and Miquelon",
  "Saint Vincent and the Grenadines",
  "Sint Eustatius",
  "Sint Maarten",
  "Trinidad and Tobago",
  "Turks and Caicos Islands",
];

const SIX_MONTH_CLUB_COUNTRIES = [
  "Andorra",
  "Angola",
  "Antigua and Barbuda",
  "Antilles",
  "Argentina",
  "Armenia",
  "Aruba",
  "Australia",
  "Austria",
  "Bahamas",
  "Barbados",
  "Belgium",
  "Belize",
  "Bermuda",
  "Bolivia",
  "Bosnia-Herzegovina",
  "Brazil",
  "Bulgaria",
  "Burma",
  "Canada",
  "Chile",
  "Colombia",
  "Costa Rica",
  "Cote d'Ivoire",
  "Croatia",
  "Cyprus",
  "Czech Republic",
  "Denmark",
  "Dominica",
  "Dominican Republic",
  "Egypt",
  "El Salvador",
  "Estonia",
  "Ethiopia",
  "Federated States of Micronesia",
  "Fiji",
  "Finland",
  "France",
  "Georgia",
  "Germany",
  "Greece",
  "Grenada",
  "Guatemala",
  "Guinea",
  "Guyana",
  "Haiti",
  "Hong Kong",
  "Hungary",
  "Iceland",
  "India",
  "Indonesia",
  "Ireland",
  "Israel",
  "Italy",
  "Jamaica",
  "Japan",
  "Kosovo",
  "Latvia",
  "Lebanon",
  "Libya",
  "Liechtenstein",
  "Lithuania",
  "Luxembourg",
  "Macau",
  "Madagascar",
  "Maldives",
  "Malaysia",
  "Malta",
  "Mauritania",
  "Mauritius",
  "Mexico",
  "Monaco",
  "Mongolia",
  "Montenegro",
  "Mozambique",
  "Nepal",
  "Netherlands",
  "New Zealand",
  "Nicaragua",
  "Nigeria",
  "North Macedonia",
  "Norway",
  "Pakistan",
  "Palau",
  "Panama",
  "Papua New Guinea",
  "Paraguay",
  "Peru",
  "Philippines",
  "Poland",
  "Portugal",
  "Qatar",
  "Romania",
  "Russia",
  "San Marino",
  "Saudi Arabia",
  "Serbia",
  "Seychelles",
  "Singapore",
  "Slovakia",
  "Slovenia",
  "South Africa",
  "South Korea",
  "Spain",
  "Sri Lanka",
  "Saint Kitts and Nevis",
  "Saint Lucia",
  "Saint Vincent and the Grenadines",
  "Suriname",
  "Sweden",
  "Switzerland",
  "Taiwan",
  "Thailand",
  "Trinidad and Tobago",
  "Tunisia",
  "Turkey",
  "Tuvalu",
  "Ukraine",
  "United Arab Emirates",
  "United Kingdom",
  "Uruguay",
  "Uzbekistan",
  "Vatican City-Holy See",
  "Venezuela",
  "Zimbabwe",
];

export {chatWithDocuments} from "./chat";
export {
  evaluateFicaRefundCase,
  generateFicaEmployerPacket,
  generateFicaRefundPacket,
} from "./ficaRefund";
export {
  buildReportingChecklist,
  completeReportingWizard,
  generateSevpRelationshipDraft,
  prepareReportingWizard,
} from "./reportingWizard";
export {
  exportI983OfficialPdf,
  generateI983SectionDrafts,
  getI983AssistantBundle,
} from "./i983Assistant";
export {
  publishApprovedPolicyAlertCandidate,
  syncNotificationDevice,
  syncPolicyAlertCandidates,
} from "./policyAlerts";
export {
  getH1bDashboardBundle,
  saveEVerifySnapshot,
  searchH1bEmployerHistory,
  syncH1bEmployerDataHub,
} from "./h1bDashboard";
export {getScenarioSimulatorBundle} from "./scenarioSimulator";
export {getVisaPathwayPlannerBundle} from "./visaPathwayPlanner";
export {
  archiveUscisCase,
  getUscisTrackerAvailability,
  pollTrackedUscisCases,
  refreshUscisCase,
  removeUscisCase,
  syncUscisCaseTrackerDevice,
  trackUscisCase,
} from "./uscisCaseTracker";
