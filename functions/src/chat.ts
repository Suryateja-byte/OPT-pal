import { onCall, HttpsError } from "firebase-functions/v2/https";
import { VertexAI } from "@google-cloud/vertexai";
import { firestore } from "./documentSecurity";

const vertexAI = new VertexAI({
  project: process.env.GCLOUD_PROJECT || "opt-pal",
  location: "us-central1",
});
const model = vertexAI.getGenerativeModel({model: "gemini-1.5-flash-001"});

type StoredDocument = {
  id: string;
  processingMode?: string;
  extractedData?: Record<string, unknown>;
  fileName?: string;
  userTag?: string;
  documentType?: string;
  summary?: string;
};

export const chatWithDocuments = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in to chat.");
  }

  const userId = request.auth.uid;
  const query = typeof request.data.query === "string" ? request.data.query.trim() : "";
  if (!query) {
    throw new HttpsError("invalid-argument", "Query must be a non-empty string.");
  }

  try {
    const docsSnapshot = await firestore.collection("users").doc(userId).collection("documents").get();
    if (docsSnapshot.empty) {
      return {text: "You don't have any documents saved yet. Please upload one first.", documentRefs: []};
    }

    const allDocuments: StoredDocument[] = docsSnapshot.docs.map((doc) => ({
      id: doc.id,
      ...(doc.data() as Omit<StoredDocument, "id">),
    }));
    const aiVisibleDocuments = allDocuments.filter((doc) => doc.processingMode !== "storage_only");
    if (aiVisibleDocuments.length === 0) {
      return {
        text: "Your vault currently has storage-only documents. Upload and analyze a document if you want AI answers from document content.",
        documentRefs: findRelevantDocumentRefs(query, allDocuments),
      };
    }

    const documentContext = aiVisibleDocuments.map((doc) => {
      const extractedData = doc.extractedData && Object.keys(doc.extractedData).length > 0 ?
        JSON.stringify(doc.extractedData) :
        "(No structured data found.)";
      return [
        `Document ID: ${doc.id}`,
        `File Name: ${doc.fileName || doc.id}`,
        `Display Name: ${doc.userTag || doc.documentType || "Unknown"}`,
        `Summary: ${doc.summary || "No summary available."}`,
        `Extracted Data: ${extractedData}`,
      ].join("\n");
    }).join("\n\n");

    const prompt = `
You are a helpful AI assistant for an OPT student.

Use only the document information below. Do not invent document links.
If the user asks to open or view a document, mention that internal document references are attached when available.

${documentContext}

User Query: "${query}"
    `;

    const result = await model.generateContent(prompt);
    const text = result.response.candidates?.[0]?.content?.parts?.[0]?.text ||
      "I couldn't generate a response.";

    return {
      text,
      documentRefs: findRelevantDocumentRefs(query, allDocuments),
    };
  } catch (error: any) {
    throw new HttpsError("internal", `Failed to process chat request: ${error.message || error}`);
  }
});

function findRelevantDocumentRefs(query: string, documents: StoredDocument[]) {
  const normalizedQuery = query.toLowerCase();
  if (!/(open|show|view|download|see)/.test(normalizedQuery)) {
    return [];
  }

  const scored = documents.map((doc) => {
    const label = String(doc.userTag || doc.documentType || doc.fileName || "Document");
    const haystack = `${label} ${doc.fileName || ""} ${doc.documentType || ""}`.toLowerCase();
    let score = 0;
    if (normalizedQuery.includes(label.toLowerCase())) {
      score += 5;
    }
    for (const token of normalizedQuery.split(/\W+/).filter(Boolean)) {
      if (token.length > 2 && haystack.includes(token)) {
        score += 1;
      }
    }
    return {
      documentId: String(doc.id),
      fileName: String(doc.fileName || ""),
      label,
      score,
    };
  });

  return scored
    .filter((doc) => doc.score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, 3)
    .map(({documentId, fileName, label}) => ({documentId, fileName, label}));
}
