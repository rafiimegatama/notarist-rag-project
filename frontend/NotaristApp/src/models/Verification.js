// Human verification / QC checklist for a bundle (Sprint 5, Task 2).
//
// New in Sprint 5, and it closes a genuine architectural gap rather than a field rename:
//
//   VerificationService.getChecklist() currently calls api/bundles#getBundleDocuments(). It returns
//   DOCUMENTS and the screen derives a checklist from them, because when it was written there was no
//   verification backend. There is now: VerificationController serves a real VerificationResponse
//   with a first-class `checklist[]`, per-item decisions, `progress` and `summary`.
//
// So the frontend has been deriving a checklist that the backend already owns. This normalizer
// models the real thing; wiring it up is gated on FEATURES.verificationEndpoint (see the service).
//
// Verified against backend/notarist-verification/.../api/response/VerificationResponse.java.
import { pick, str, num, count, isoDate, list, obj, bool, withExtras, makeNormalizer } from './normalize';
import { toList } from '../api/envelope';

const ITEM_CONSUMED = [
  'itemId', 'id', 'category', 'label', 'description', 'decision', 'status', 'comment',
  'required', 'sortOrder', 'documentId', 'fieldId', 'reviewedAt', 'reviewedBy',
];

export function normalizeChecklistItem(raw = {}) {
  const rawId = pick(raw, ['itemId', 'id']);
  const out = {
    id: rawId === null ? null : String(rawId),
    category: str(pick(raw, ['category']), null),
    label: str(pick(raw, ['label']), null),
    description: str(pick(raw, ['description']), null),
    // The reviewer's call on this item. Left raw (server vocabulary) and NOT defaulted: an
    // undecided item and an item decided "OK" are different facts, and defaulting would erase that.
    decision: str(pick(raw, ['decision', 'status']), null),
    comment: str(pick(raw, ['comment']), null),
    required: bool(pick(raw, ['required']), false),
    // Server-assigned display order — the backend sorts by it before serializing.
    sortOrder: num(pick(raw, ['sortOrder']), null),
    documentId: str(pick(raw, ['documentId']), null),
    fieldId: str(pick(raw, ['fieldId']), null),
    reviewedAt: isoDate(pick(raw, ['reviewedAt']), null),
    reviewedBy: str(pick(raw, ['reviewedBy']), null),
  };
  return withExtras(out, raw, ITEM_CONSUMED);
}

export const ChecklistItemNormalizer = makeNormalizer(normalizeChecklistItem);

const CONSUMED = [
  'bundleId', 'verificationId', 'status', 'reviewerId', 'reviewedAt',
  'progress', 'summary', 'checklist', 'categories', 'items',
];

export function normalizeVerification(raw = {}) {
  const source = obj(raw, {}) || {};
  const progress = obj(pick(source, ['progress']), null);
  const summary = obj(pick(source, ['summary']), null);

  // `checklist` is the real key; `items` covers the fixture/legacy spelling.
  const checklist = ChecklistItemNormalizer.list(toList(source, ['checklist', 'items', 'content']));

  const out = {
    bundleId: str(pick(source, ['bundleId']), null),
    verificationId: str(pick(source, ['verificationId']), null),
    status: str(pick(source, ['status']), null),
    reviewerId: str(pick(source, ['reviewerId']), null),
    reviewedAt: isoDate(pick(source, ['reviewedAt']), null),

    checklist,

    // Progress is nested and every field is guarded: a partial response must not crash a progress
    // bar. Counts fall back to what the checklist itself shows rather than to zero — 0/0 reads as
    // "nothing to do", which is a claim, where a derived count is at least consistent with the rows
    // on screen.
    progress: {
      total: count(pick(progress, ['total']), checklist.length),
      completed: count(pick(progress, ['completed']), checklist.filter((i) => i.decision !== null).length),
      percent: num(pick(progress, ['percent']), null),
    },

    // Left as a loose object: the summary's field set is still moving on the backend, and
    // withExtras below preserves whatever arrives.
    summary: summary ? { ...summary } : null,

    // Server-side grouping of the same items. Kept because it carries the server's category order,
    // which a client-side groupBy would lose.
    categories: list(toList(source, ['categories']), (c) => {
      const g = obj(c, {});
      if (!g) return null;
      return {
        category: str(pick(g, ['category', 'name']), null),
        items: ChecklistItemNormalizer.list(toList(g, ['items', 'checklist'])),
      };
    }),
  };
  return withExtras(out, source, CONSUMED);
}

export const VerificationNormalizer = makeNormalizer(normalizeVerification);
