// OCRService — OCR-extracted fields + per-field review decisions for a document. Delegates to
// api/documents, which is HTTP-only behind FEATURES.ocrReviewEndpoint: the flag being off throws
// UNAVAILABLE rather than serving a mock, because inventing an NIK is fabricating evidence.
//
// `usingMock` is gone (Sprint 7). It was `!FEATURES.ocrReviewEndpoint` and OcrReviewScreen drew a
// MockBanner from it — but with the mock removed there is no mock to warn about: flag off now means
// the screen shows ErrorState and never reaches the banner. A `usingMock: true` sitting next to an
// api module that cannot produce mock data is config claiming a state the code does not have.
import { getOcrFields, submitFieldDecision } from '../api/documents';

export const OCRService = {
  getFields: (documentId) => getOcrFields(documentId),
  submitFieldDecision: (documentId, fieldId, decision, value) => submitFieldDecision(documentId, fieldId, decision, value),
};
