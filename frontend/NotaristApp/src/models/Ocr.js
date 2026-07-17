// OCR-extracted fields awaiting human review (Sprint 5, Task 2).
//
// New in Sprint 5. Verified against backend/notarist-review/.../api/response/OcrFieldResponse.java.
//
// This DTO is the happy case of the sprint: Claude 1 shaped it deliberately around this UI. Its own
// comments say so — `status` is annotated "frontend vocabulary: PENDING|NEEDS_CHECK|APPROVED|
// REJECTED" and Bbox is annotated "Keys are x,y,w,h to match the existing frontend overlay". So the
// normalizer's job here is mostly guarding, not translating.
//
// It carries BOTH vocabularies at once, which is the one subtlety:
//   value / status              -> the frontend-facing pair
//   extractedValue / correctedValue / decision / confidenceLevel -> the domain's own pair
// They can disagree if the backend updates one and not the other, so this reads the frontend pair
// first and falls back to the domain pair, rather than mixing them per-field.
import { pick, str, num, oneOf, list, obj, withExtras, makeNormalizer } from './normalize';
import { toList } from '../api/envelope';

export const FIELD_STATUS = ['PENDING', 'NEEDS_CHECK', 'APPROVED', 'REJECTED'];
export const CONFIDENCE_LEVEL = ['HIGH', 'MEDIUM', 'LOW'];

// Derive a band when the server sends a raw confidence but no level. Thresholds match the backend's
// own HIGH/MEDIUM/LOW split; a field below 0.75 is what the review queue exists for.
export function confidenceBand(confidence) {
  if (confidence === null) return null;
  if (confidence >= 0.9) return 'HIGH';
  if (confidence >= 0.75) return 'MEDIUM';
  return 'LOW';
}

const BBOX_KEYS = ['x', 'y', 'w', 'h'];

/**
 * The overlay rectangle. All four keys must be present and finite or the box is null — a partial
 * box would draw a rectangle in the wrong place over a scanned deed, which is worse than drawing
 * none. This is the one field where "normalize what you can" is the wrong instinct.
 */
export function normalizeBbox(raw) {
  const b = obj(raw, null);
  if (!b) return null;
  const out = {};
  for (const k of BBOX_KEYS) {
    const v = num(pick(b, [k]), null);
    if (v === null) return null;
    out[k] = v;
  }
  return out;
}

const CONSUMED = [
  'id', 'fieldId', 'label', 'fieldName', 'value', 'extractedValue', 'correctedValue',
  'confidence', 'confidenceLevel', 'status', 'decision', 'bbox', 'rejectionReason', 'page',
];

export function normalizeOcrField(raw = {}) {
  const rawId = pick(raw, ['id', 'fieldId']);
  const confidence = num(pick(raw, ['confidence']), null);

  const out = {
    id: rawId === null ? null : String(rawId),
    // `label` is the display name; `fieldName` is the machine name. Neither is invented.
    label: str(pick(raw, ['label', 'fieldName']), null),
    fieldName: str(pick(raw, ['fieldName']), null),

    // A correction supersedes the extraction — that is the entire point of the review queue.
    value: str(pick(raw, ['correctedValue', 'value', 'extractedValue']), null),
    extractedValue: str(pick(raw, ['extractedValue', 'value']), null),
    correctedValue: str(pick(raw, ['correctedValue']), null),

    confidence,
    confidenceLevel: oneOf(pick(raw, ['confidenceLevel']), CONFIDENCE_LEVEL, null) ?? confidenceBand(confidence),

    // Frontend vocabulary first, domain `decision` as the fallback. Unknown values fall to null
    // rather than PENDING: "we do not know this field's state" must not read as "not yet reviewed".
    status: oneOf(pick(raw, ['status', 'decision']), FIELD_STATUS, null),
    decision: str(pick(raw, ['decision']), null),
    rejectionReason: str(pick(raw, ['rejectionReason']), null),

    bbox: normalizeBbox(pick(raw, ['bbox'])),
    page: num(pick(raw, ['page']), null),
  };
  return withExtras(out, raw, CONSUMED);
}

export const OcrFieldNormalizer = makeNormalizer(normalizeOcrField);

const DOC_CONSUMED = ['documentId', 'id', 'fields', 'items', 'pageCount', 'pages', 'imageUrl', 'status', 'overallConfidence'];

/** The OCR review payload for one document: the field list plus whatever frames it. */
export function normalizeOcrDocument(raw = {}) {
  const source = obj(raw, {}) || {};
  const rawId = pick(source, ['documentId', 'id']);
  const fields = OcrFieldNormalizer.list(toList(source, ['fields', 'items', 'content']));

  const out = {
    documentId: rawId === null ? null : String(rawId),
    fields,
    pageCount: num(pick(source, ['pageCount', 'pages']), null),
    imageUrl: str(pick(source, ['imageUrl']), null),
    status: str(pick(source, ['status']), null),
    // Server value preferred; else the mean of what we actually have. null when there is nothing to
    // average — never 0, which would render as "0% confident" rather than "unknown".
    overallConfidence: num(pick(source, ['overallConfidence']), null) ?? meanConfidence(fields),
  };
  return withExtras(out, source, DOC_CONSUMED);
}

function meanConfidence(fields) {
  const known = fields.map((f) => f.confidence).filter((c) => c !== null);
  if (!known.length) return null;
  return known.reduce((a, b) => a + b, 0) / known.length;
}

export const OCRNormalizer = makeNormalizer(normalizeOcrDocument);
