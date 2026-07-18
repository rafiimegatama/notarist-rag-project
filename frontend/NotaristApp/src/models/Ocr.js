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
import { pick, str, num, count, oneOf, isoDate, obj, triBool, withExtras, makeNormalizer } from './normalize';
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

const AUTHORITY_CONSUMED = ['id', 'role', 'name', 'decision', 'at', 'authorityType', 'content', 'confidence'];

/**
 * One row of the "Timeline Direksi" (AuthorityTimelineEntryResponse).
 *
 * Like OcrFieldResponse, this DTO was shaped around the existing component — its javadoc says "Shape
 * matches what the existing DirectorTimeline component reads: id, role, name, decision, at" — so this
 * guards rather than translates. `decision` is already mapped to the frontend vocabulary server-side
 * (CONFIRMED -> APPROVED), which is why it is read as-is.
 */
export function normalizeAuthorityEntry(raw) {
  const a = obj(raw, null);
  if (!a) return null;
  const rawId = pick(a, ['id']);
  const out = {
    id: rawId === null ? null : String(rawId),
    role: str(pick(a, ['role']), null),
    name: str(pick(a, ['name']), null),
    // PENDING | APPROVED | REJECTED. NOT defaulted to PENDING: DirectorTimeline renders anything
    // that is not APPROVED/REJECTED as "Menunggu", so an unknown value already degrades honestly,
    // and coercing it here would erase the difference between "waiting" and "we do not know".
    decision: str(pick(a, ['decision']), null),
    at: isoDate(pick(a, ['at']), null),
    authorityType: str(pick(a, ['authorityType']), null),
    content: str(pick(a, ['content']), null),
    confidence: num(pick(a, ['confidence']), null),
  };
  return withExtras(out, a, AUTHORITY_CONSUMED);
}

export const AuthorityEntryNormalizer = makeNormalizer(normalizeAuthorityEntry);

const DOC_CONSUMED = [
  'documentId', 'id', 'documentName', 'fields', 'items', 'pageCount', 'pages', 'imageUrl',
  'status', 'reviewStatus', 'overallConfidence', 'stampDetected', 'signatureDetected',
  'authorityTimeline', 'authorityItems', 'reviewId', 'reviewerId', 'reviewedAt', 'progress',
];

/**
 * The OCR review payload for one document (OcrReviewResponse).
 *
 * Sprint 6 completed this. It previously modelled SIX fields and OcrReviewScreen reads ELEVEN, so
 * wiring this normalizer in — which is what it was written for — would have silently blanked:
 *
 *   documentName        the preview pane's caption ("Pratinjau undefined")
 *   stampDetected       a legal-signal chip
 *   signatureDetected   a legal-signal chip
 *   authorityTimeline   the ENTIRE Timeline Direksi — the board approval trail
 *
 * That is the failure this file exists to prevent, hiding inside the fix for it: the normalizer was
 * unreferenced (api/documents#getOcrFields returned the raw payload), so nothing exercised the gap
 * and it looked finished. A normalizer narrower than its DTO is not a partial implementation; it is a
 * silent field filter.
 */
export function normalizeOcrDocument(raw = {}) {
  const source = obj(raw, {}) || {};
  const rawId = pick(source, ['documentId', 'id']);
  const fields = OcrFieldNormalizer.list(toList(source, ['fields', 'items', 'content']));

  const out = {
    documentId: rawId === null ? null : String(rawId),
    documentName: str(pick(source, ['documentName']), null),
    fields,
    pageCount: num(pick(source, ['pageCount', 'pages']), null),
    imageUrl: str(pick(source, ['imageUrl']), null),
    status: str(pick(source, ['status', 'reviewStatus']), null),

    // Legal signals on the scan. triBool, not bool: false says "we looked and there is no notarial
    // stamp on this deed" — a finding — while absent says we do not know. Defaulting the second to
    // the first would have the app assert a missing stamp it never checked for.
    stampDetected: triBool(pick(source, ['stampDetected'])),
    signatureDetected: triBool(pick(source, ['signatureDetected'])),

    // Always an array, like citations: DirectorTimeline maps over it.
    authorityTimeline: AuthorityEntryNormalizer.list(toList(source, ['authorityTimeline', 'authorityItems'])),

    // Server value preferred; else the mean of what we actually have. null when there is nothing to
    // average — never 0, which would render as "0% confident" rather than "unknown".
    overallConfidence: num(pick(source, ['overallConfidence']), null) ?? meanConfidence(fields),

    // Additive review detail the DTO carries. Read so it survives rather than landing in __extra.
    reviewId: str(pick(source, ['reviewId']), null),
    reviewerId: str(pick(source, ['reviewerId']), null),
    reviewedAt: isoDate(pick(source, ['reviewedAt']), null),
    progress: normalizeOcrProgress(pick(source, ['progress'])),
  };
  return withExtras(out, source, DOC_CONSUMED);
}

const PROGRESS_CONSUMED = ['total', 'accepted', 'corrected', 'rejected', 'remaining'];

/** OcrReviewProgressResponse — accepted/corrected/rejected/remaining out of total. */
function normalizeOcrProgress(raw) {
  const p = obj(raw, null);
  if (!p) return null;
  const out = {
    total: count(pick(p, ['total']), 0),
    // null, not 0, for the individual counters: 0 is a claim ("nothing was rejected"), and these
    // exist precisely to report that something WAS.
    accepted: num(pick(p, ['accepted']), null),
    corrected: num(pick(p, ['corrected']), null),
    rejected: num(pick(p, ['rejected']), null),
    remaining: num(pick(p, ['remaining']), null),
  };
  return withExtras(out, p, PROGRESS_CONSUMED);
}

function meanConfidence(fields) {
  const known = fields.map((f) => f.confidence).filter((c) => c !== null);
  if (!known.length) return null;
  return known.reduce((a, b) => a + b, 0) / known.length;
}

export const OCRNormalizer = makeNormalizer(normalizeOcrDocument);
