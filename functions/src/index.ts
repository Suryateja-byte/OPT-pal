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
      await doc.ref.update({
        extractedData: structuredData.data,
        documentType: structuredData.documentType,
        summary: structuredData.summary,
        processingStatus: "processed",
        processingError: "",
        processedAt: Date.now(),
      });
      processedCount++;
    } catch (error) {
      logger.error("Failed to reprocess secure document", {documentId: doc.id, error});
    }
  }

  return {message: `Reprocessed ${processedCount} documents.`};
});

async function structureDataWithLLM(text: string): Promise<{ documentType: string; data: unknown; summary: string }> {
  const prompt = `
You are an expert document parsing API. Respond only in valid JSON.

Analyze the OCR text below and return:
1. "document_type": specific document type.
2. "summary": one concise sentence.
3. "data": key-value pairs with the most useful fields in snake_case.

If the document is an I-20 or an EAD card, include these canonical keys when present:
- sevis_id
- school_name
- cip_code
- opt_type
- opt_start_date
- opt_end_date
- ead_category
- uscis_number

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
  const canonicalData: Record<string, unknown> = {...data};

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

  if (sevisId) {
    canonicalData.sevis_id = sevisId.toUpperCase();
  }
  if (schoolName) {
    canonicalData.school_name = schoolName;
  }
  if (cipCode) {
    canonicalData.cip_code = cipCode;
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

  if (normalizedType === "I-20 Form") {
    canonicalData.document_type = "i20";
  } else if (normalizedType === "EAD Card") {
    canonicalData.document_type = "ead";
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

function normalizeProcessingMode(value: unknown): "storage_only" | "analyze" {
  if (value === "storage_only" || value === "analyze") {
    return value;
  }
  throw new HttpsError("invalid-argument", "processingMode must be storage_only or analyze.");
}

export {chatWithDocuments} from "./chat";
