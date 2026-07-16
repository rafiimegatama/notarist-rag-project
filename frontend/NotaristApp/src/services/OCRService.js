// OCRService — OCR-extracted fields + per-field review decisions for a document. Delegates to
// api/documents (mock|http behind FEATURES.ocrReviewEndpoint).
import { FEATURES } from '../constants/config';
import { getOcrFields, submitFieldDecision } from '../api/documents';

export const OCRService = {
  usingMock: !FEATURES.ocrReviewEndpoint,
  getFields: (documentId) => getOcrFields(documentId),
  submitFieldDecision: (documentId, fieldId, decision, value) => submitFieldDecision(documentId, fieldId, decision, value),
};
