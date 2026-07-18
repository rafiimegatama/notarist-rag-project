import client from './client';
import * as Crypto from 'expo-crypto';
import { File } from 'expo-file-system';
import { FEATURES } from '../constants/config';
import { mock, requireEndpoint } from './_support';
import { unwrap, toList } from './envelope';
import { normalizePage } from './pagination';
import { normalizeOcrDocument } from '../models/Ocr';

// GET /documents?documentType&status&page&size -> ApiResponse<PageResponse<DocumentLegalResponse>>
export async function listDocuments(page = 0, size = 20) {
  // Sprint 5, Task 10: was ungated (the dead `LIVE` block). No mock — a fabricated document list on
  // a notarial platform is data invention, not a placeholder.
  requireEndpoint(FEATURES.documentsEndpoint, 'documents');
  const response = await client.get('/documents', { params: { page, size } });
  // Sprint 6: this returned the RAW body and made DocumentsScreen dig out `data.data.items` itself —
  // carried as acknowledged debt because "changing it means touching a screen". The debt had teeth:
  // the screen also did `data.data?.page?.totalPages ?? 1`, so the one module that skipped
  // normalizePage was also the one that silently truncated at page 1 whenever a total was absent.
  //
  // Paid off here rather than described again. Every api module now returns a normalized shape and
  // the envelope is unwrapped in exactly one layer.
  const payload = unwrap(response, null);
  return {
    items: toList(payload, ['items', 'content']),
    page: normalizePage(payload, { page, size }),
  };
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
//
// OcrReviewController (@RequestMapping(API_BASE_PATH + "/documents/{documentId}/ocr")):
//   GET    ""                  -> ApiResponse<OcrReviewResponse>
//   GET    /summary            -> ApiResponse<OcrReviewSummaryResponse>
//   PUT    /fields/{fieldId}   -> ApiResponse<OcrReviewResponse>   body { decision, value, reason }
//   PATCH  /status             -> ApiResponse<OcrReviewResponse>   body { targetStatus }
//
// Returns extracted fields with confidence, bounding boxes, stamp/signature detection and the
// authority (Direksi) approval trail.

// GET /documents/{documentId}/ocr -> ApiResponse<OcrReviewResponse>
//
// Sprint 6 wired models/Ocr.js in here. It was imported by NOTHING: this returned `response.data.data`
// raw, so the normalizer written and shipped in Sprint 5 to model this exact DTO had never once run.
// Both halves were broken and each hid the other — the normalizer dropped four fields the screen
// renders (documentName, stampDetected, signatureDetected, authorityTimeline), which nobody noticed
// because nothing called it. See models/Ocr.js#normalizeOcrDocument.
//
// Both paths normalize, mock included. A mock that skips the normalizer is a mock of a different
// contract — and it is the only thing exercising this screen while ocrReviewEndpoint is false.
export async function getOcrFields(documentId) {
  // No mock path, for the reason api/search#runSearch and api/assistant#askAssistant have none: OCR
  // fields are extracted legal FACTS — an NIK, a name, a certificate number — and inventing them is
  // fabricating evidence, not stubbing a screen.
  //
  // Sprint 7 removed one. It read `MOCK_OCR_FIELDS[documentId] ?? MOCK_OCR_FIELDS['doc-1']`, so the
  // `??` handed the 'doc-1' fixture to EVERY unknown id — and now that the case/bundle chain is live,
  // every id IS an unknown real UUID. Verified against the running backend: OCR Review on a real
  // bundle rendered "KTP Debitur.pdf" and a fabricated NIK, under a real case number, guarded only by
  // a banner that a notary checking an identity document should not have to notice to stay safe.
  //
  // The route EXISTS (OcrReviewController). It answers 404 OCR_REVIEW_NOT_FOUND because no OcrReview
  // is ever provisioned — see the flag's note in constants/config.js. Until that backend gap closes,
  // "belum tersedia" is the only true thing this screen can say.
  requireEndpoint(FEATURES.ocrReviewEndpoint, 'ocr-review');
  const response = await client.get(`/documents/${documentId}/ocr`);
  return normalizeOcrDocument(unwrap(response, null));
}

// PUT /documents/{docId}/ocr/fields/{fieldId} -> ApiResponse<OcrReviewResponse>
//   body: { decision: APPROVED|REJECTED|NEEDS_CHECK, value?, reason? }
//
// The decision vocabulary is verified, not assumed: ReviewFieldRequest's javadoc and
// FieldDecisionTranslator both accept the frontend names alongside the domain FieldDecision names.
//
// Returns the whole recomputed review, so it is normalized through the same path as the GET — a
// caller can use the response to refresh state rather than re-fetching.
export async function submitFieldDecision(documentId, fieldId, decision, value) {
  // Gated with the GET above: the mock here ACKNOWLEDGED a decision nothing recorded, echoing the
  // payload back with a timestamp so the screen ticked over as though a notary's approval of an
  // extracted field had been stored. A write that only pretends to persist is worse than a failure.
  requireEndpoint(FEATURES.ocrReviewEndpoint, 'ocr-review');
  const response = await client.put(`/documents/${documentId}/ocr/fields/${fieldId}`, { decision, value });
  return normalizeOcrDocument(unwrap(response, null));
}
