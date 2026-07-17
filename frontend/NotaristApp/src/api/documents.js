import client from './client';
import * as Crypto from 'expo-crypto';
import { File } from 'expo-file-system';
import { FEATURES } from '../constants/config';
import { mock, requireEndpoint } from './_support';
import { unwrap } from './envelope';
import { MOCK_OCR_FIELDS } from '../mocks/fixtures';

export async function listDocuments(page = 0, size = 20) {
  // Sprint 5, Task 10: was ungated (the dead `LIVE` block). No mock — a fabricated document list on
  // a notarial platform is data invention, not a placeholder.
  requireEndpoint(FEATURES.documentsEndpoint, 'documents');
  const response = await client.get('/documents', { params: { page, size } });
  // NOTE: returns the raw body, not the unwrapped payload — DocumentsScreen reads
  // `response.data.data.items` itself. Left as-is deliberately: changing it means touching a screen,
  // and this sprint does not rewrite UI. Logged as debt in the sprint report.
  return response.data;
}

export async function getDocument(documentId) {
  requireEndpoint(FEATURES.documentsEndpoint, 'documents');
  const response = await client.get(`/documents/${documentId}`);
  return unwrap(response, null);
}

export async function computeFileSha256(fileUri) {
  const file = new File(fileUri);
  const buffer = await file.arrayBuffer();
  const digest = await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function initiateUpload(payload) {
  // payload: { originalFilename, checksumSha256, documentType, classificationLevel }
  // Sprint 5, Task 10: ingestion is a WRITE. It has no mock path and must never acquire one — a
  // fake "upload succeeded" would tell a notary their deed is filed when nothing left the device.
  requireEndpoint(FEATURES.ingestEndpoint, 'ingest');
  const response = await client.post('/ingest', payload);
  return unwrap(response, null);
}

export async function uploadFileToSignedUrl(signedUrl, fileUri, requiredHeaders) {
  const file = new File(fileUri);
  const result = await file.upload(signedUrl, {
    httpMethod: 'PUT',
    headers: requiredHeaders,
  });
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`File upload failed with status ${result.status}`);
  }
  return result;
}

export async function confirmUpload(jobId, checksumSha256) {
  requireEndpoint(FEATURES.ingestEndpoint, 'ingest');
  const response = await client.post(`/ingest/${jobId}/confirm`, { checksumSha256 });
  return response.data;
}

export async function getIngestionStatus(jobId) {
  requireEndpoint(FEATURES.ingestEndpoint, 'ingest');
  const response = await client.get(`/ingest/${jobId}/status`);
  return unwrap(response, null);
}

// --- OCR Review (document-scoped field extraction) ---
// No /ocr endpoint yet (FEATURES.ocrReviewEndpoint). Returns extracted fields with confidence,
// bounding boxes, stamp/signature detection and the authority (Direksi) approval trail.
export async function getOcrFields(documentId) {
  if (FEATURES.ocrReviewEndpoint) {
    const response = await client.get(`/documents/${documentId}/ocr`);
    return response.data.data;
  }
  const found = MOCK_OCR_FIELDS[documentId] ?? MOCK_OCR_FIELDS['doc-1'];
  return mock({ ...found, documentId }, { label: 'ocr-fields', delay: 450 });
}

// PUT /documents/{docId}/ocr/fields/{fieldId} { decision: APPROVED|REJECTED|NEEDS_CHECK, value? }
export async function submitFieldDecision(documentId, fieldId, decision, value) {
  if (FEATURES.ocrReviewEndpoint) {
    const response = await client.put(`/documents/${documentId}/ocr/fields/${fieldId}`, { decision, value });
    return response.data.data;
  }
  return mock({ documentId, fieldId, decision, value, at: new Date().toISOString() }, { label: 'field-decision', delay: 200 });
}
