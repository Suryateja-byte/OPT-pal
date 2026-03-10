import * as admin from "firebase-admin";
import { ImageAnnotatorClient } from "@google-cloud/vision";
import { createCipheriv, createDecipheriv, randomBytes, randomUUID } from "crypto";

declare function require(moduleName: string): any;

if (admin.apps.length === 0) {
  admin.initializeApp();
}

export const firestore = admin.firestore();
export const storage = admin.storage();
export const visionClient = new ImageAnnotatorClient();

const {KeyManagementServiceClient} = require("@google-cloud/kms");
const kmsClient = new KeyManagementServiceClient();
const AES_AUTH_TAG_BYTES = 16;
const AES_IV_BYTES = 12;
const DEFAULT_KMS_LOCATION = "us-central1";
const DEFAULT_KMS_KEY_RING = "optpal";
const DEFAULT_KMS_KEY = "document-content";

export function documentRef(userId: string, documentId: string) {
  return firestore.collection("users").doc(userId).collection("documents").doc(documentId);
}

export function storagePathForDocument(userId: string, documentId: string) {
  return `users/${userId}/documents/${documentId}.enc`;
}

export async function wrapDocumentKey(documentKeyBase64: string): Promise<string> {
  const plaintext = Buffer.from(documentKeyBase64, "base64");
  const [response] = await kmsClient.encrypt({
    name: getKmsKeyName(),
    plaintext,
  });
  return Buffer.from(response.ciphertext as Uint8Array).toString("base64");
}

export async function unwrapDocumentKey(wrappedDocumentKey: string): Promise<Buffer> {
  const ciphertext = Buffer.from(wrappedDocumentKey, "base64");
  const [response] = await kmsClient.decrypt({
    name: getKmsKeyName(),
    ciphertext,
  });
  return Buffer.from(response.plaintext as Uint8Array);
}

export async function loadStoredDocumentBytes(
  userId: string,
  documentId: string,
  storagePath: string,
  wrappedDocumentKey?: string
): Promise<Buffer> {
  const bucket = storage.bucket();
  const [storedBytes] = await bucket.file(storagePath).download();
  if (!wrappedDocumentKey) {
    return storedBytes;
  }
  const documentKey = await unwrapDocumentKey(wrappedDocumentKey);
  return decryptDocumentBytes(storedBytes, documentKey);
}

export async function extractTextFromDocument(
  bytes: Buffer,
  contentType: string
): Promise<string | undefined> {
  if (contentType === "application/pdf") {
    const [result] = await visionClient.batchAnnotateFiles({
      requests: [
        {
          inputConfig: {
            mimeType: contentType,
            content: bytes.toString("base64"),
          },
          features: [{type: "DOCUMENT_TEXT_DETECTION"}],
        } as any,
      ],
    });

    const text = (result.responses || [])
      .flatMap((fileResponse: any) => fileResponse.responses || [])
      .map((pageResponse: any) => pageResponse.fullTextAnnotation?.text || "")
      .join("\n")
      .trim();
    return text || undefined;
  }

  const [result] = await visionClient.documentTextDetection({
    image: {content: bytes.toString("base64")},
  } as any);
  return result.fullTextAnnotation?.text || undefined;
}

export function sanitizeInlineFileName(fileName: string): string {
  return fileName.replace(/[^A-Za-z0-9._-]/g, "_");
}

export async function saveGeneratedSecureDocument(input: {
  userId: string;
  fileName: string;
  userTag: string;
  contentType: string;
  documentCategory: string;
  chatEligible: boolean;
  bytes: Buffer;
}): Promise<{documentId: string; storagePath: string; uploadedAt: number}> {
  const documentId = randomUUID();
  const storagePath = storagePathForDocument(input.userId, documentId);
  const keyBytes = randomBytes(32);
  const encryptedBytes = encryptDocumentBytes(input.bytes, keyBytes);
  const wrappedDocumentKey = await wrapRawDocumentKey(keyBytes);
  const uploadedAt = Date.now();

  await storage.bucket().file(storagePath).save(encryptedBytes, {
    resumable: false,
    metadata: {
      contentType: "application/octet-stream",
      metadata: {
        documentId,
        originalContentType: input.contentType,
      },
    },
  });

  await documentRef(input.userId, documentId).set({
    fileName: input.fileName,
    userTag: input.userTag,
    storagePath,
    downloadUrl: "",
    contentType: input.contentType,
    byteSize: input.bytes.length,
    encryptionVersion: 1,
    processingMode: "storage_only",
    processingConsentAcceptedAt: null,
    consentProviders: [],
    processingStatus: "stored",
    processingError: "",
    documentType: "",
    documentCategory: input.documentCategory,
    chatEligible: input.chatEligible,
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
    uploadedAt,
  };
}

function getKmsKeyName(): string {
  const configured = process.env.DOCUMENT_KMS_KEY_NAME;
  if (configured) {
    return configured;
  }

  const projectId =
    process.env.GCLOUD_PROJECT ||
    process.env.GCP_PROJECT ||
    admin.app().options.projectId ||
    "opt-pal";

  return `projects/${projectId}/locations/${DEFAULT_KMS_LOCATION}/keyRings/${DEFAULT_KMS_KEY_RING}/cryptoKeys/${DEFAULT_KMS_KEY}`;
}

async function wrapRawDocumentKey(documentKey: Buffer): Promise<string> {
  const [response] = await kmsClient.encrypt({
    name: getKmsKeyName(),
    plaintext: documentKey,
  });
  return Buffer.from(response.ciphertext as Uint8Array).toString("base64");
}

function decryptDocumentBytes(encryptedBytes: Buffer, keyBytes: Buffer): Buffer {
  if (encryptedBytes.length <= AES_IV_BYTES + AES_AUTH_TAG_BYTES) {
    throw new Error("Encrypted payload is malformed.");
  }

  const iv = encryptedBytes.subarray(0, AES_IV_BYTES);
  const ciphertextWithTag = encryptedBytes.subarray(AES_IV_BYTES);
  const authTag = ciphertextWithTag.subarray(ciphertextWithTag.length - AES_AUTH_TAG_BYTES);
  const ciphertext = ciphertextWithTag.subarray(0, ciphertextWithTag.length - AES_AUTH_TAG_BYTES);

  const decipher = createDecipheriv("aes-256-gcm", keyBytes, iv);
  decipher.setAuthTag(authTag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

function encryptDocumentBytes(plainBytes: Buffer, keyBytes: Buffer): Buffer {
  const iv = randomBytes(AES_IV_BYTES);
  const cipher = createCipheriv("aes-256-gcm", keyBytes, iv);
  const encrypted = Buffer.concat([cipher.update(plainBytes), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return Buffer.concat([iv, encrypted, authTag]);
}
