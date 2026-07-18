// Human verification / QC checklist for a bundle (Sprint 5, Task 2; corrected + wired in Sprint 6).
//
// The gap this closed: the frontend used to DERIVE a checklist from the bundle's document list (one
// row per document), because when that code was written there was no verification backend. There is
// now — VerificationController serves a real VerificationResponse with a first-class `checklist[]`,
// per-item decisions, `progress` and `summary` — so the app was deriving something the backend
// already owned, and could not express a check that spans documents.
//
// Sprint 6 finished the job. Sprint 5 wrote this normalizer but left it wired to nothing, and three
// of its field names were guesses that the DTO does not send (label/required/reviewedBy — see
// normalizeChecklistItem). It is now the real read path: api/bundles#getVerification ->
// VerificationService.getChecklist -> VerificationScreen, on both the live and mock paths.
//
// Verified against backend/notarist-verification/.../api/response/VerificationResponse.java and the
// ChecklistItemResponse / VerificationProgressResponse / VerificationSummaryResponse records nested
// inside it. scripts/validate-integration.js checks all four.
import { pick, str, num, count, isoDate, list, obj, triBool, withExtras, makeNormalizer } from './normalize';
import { toList } from '../api/envelope';

const ITEM_CONSUMED = [
  'itemId', 'id', 'category', 'title', 'label', 'decision', 'status', 'comment',
  'mandatory', 'required', 'checkType', 'reviewedAt', 'reviewer', 'reviewedBy',
];

/**
 * One checklist row.
 *
 * Sprint 6 corrected three field names against ChecklistItemResponse. They had been guessed, and each
 * guess failed differently:
 *
 *   this read     backend sends   consequence before Sprint 6
 *   ------------  --------------  --------------------------------------------------------------
 *   label         title           EVERY row rendered with no label — an unidentifiable checklist
 *   required      mandatory       every item reported required:false. Read that again: the DTO
 *                                 marks MANDATORY legal checks, and this told the UI none of them
 *                                 were. Not missing data — a FALSE STATEMENT about which checks a
 *                                 notary may skip. This is the worst bug in the sprint.
 *   reviewedBy    reviewer        the reviewer's identity vanished from an audit trail
 *
 * The old spellings are kept as trailing aliases in each pick(), so the fixtures and any legacy
 * payload still resolve. New spelling always wins.
 */
export function normalizeChecklistItem(raw = {}) {
  const rawId = pick(raw, ['itemId', 'id']);
  const out = {
    id: rawId === null ? null : String(rawId),
    category: str(pick(raw, ['category']), null),
    // `title` is the DTO's field; `label` is the legacy/fixture spelling.
    title: str(pick(raw, ['title', 'label']), null),

    // The reviewer's call on this item. Left raw (server vocabulary) and NOT defaulted: an
    // undecided item and an item decided "OK" are different facts, and defaulting would erase that.
    //
    // NOT read from `status`: they are different questions. `decision` is PASS|FAIL|NOT_APPLICABLE|
    // MANUAL_REQUIRED (null while pending); `status` is PENDING|COMPLETED. Falling back to `status`
    // made a pending item's decision read as the literal 'PENDING' — a value that is not in the
    // decision vocabulary at all, and which made `decision !== null` (the progress count below)
    // report every untouched item as DECIDED. A checklist that reports itself complete before anyone
    // has looked at it is precisely the failure human verification exists to prevent.
    decision: str(pick(raw, ['decision']), null),
    // PENDING | COMPLETED — the item's own lifecycle, kept distinct from the decision above.
    status: str(pick(raw, ['status']), null),
    comment: str(pick(raw, ['comment']), null),

    // `mandatory` is the DTO's field; `required` is the legacy/fixture spelling.
    //
    // The fallback is null, NOT false. `bool(..., false)` cannot distinguish "the server says this
    // check is optional" from "the server said nothing" — and defaulting that to false is the app
    // ASSERTING a mandatory legal check is skippable. Null lets the UI say "unknown" and is the one
    // honest answer when the field is absent.
    mandatory: triBool(pick(raw, ['mandatory', 'required'])),

    // AUTOMATIC | MANUAL | … — what kind of check this is. Raw server vocabulary.
    checkType: str(pick(raw, ['checkType']), null),

    // Sprint 6 removed `sortOrder`, `documentId`, `fieldId` and `description` from this normalizer.
    // None is a component of ChecklistItemResponse, so all four resolved to null on every payload
    // forever — reads of fields the wire does not carry.
    //
    // `sortOrder` is the instructive one. It exists on the ChecklistItem DOMAIN model, which is
    // presumably why it was reached for; but VerificationResponse.from() SORTS by it and then
    // serializes a plain list, so the ordering arrives as array order and the number itself never
    // leaves the server. Reading it here would have been a silent no-op forever, and any code that
    // trusted it to sort by would have "sorted" by null and gotten the array order back — right by
    // accident. The order the backend chose is already the order of `checklist`; preserve it by not
    // reordering.
    reviewedAt: isoDate(pick(raw, ['reviewedAt']), null),
    // `reviewer` is the DTO's field (a UUID string); `reviewedBy` is the legacy spelling.
    reviewer: str(pick(raw, ['reviewer', 'reviewedBy']), null),
  };
  return withExtras(out, raw, ITEM_CONSUMED);
}

export const ChecklistItemNormalizer = makeNormalizer(normalizeChecklistItem);

const SUMMARY_CONSUMED = [
  'bundleId', 'verificationId', 'status', 'progress', 'completable', 'blocking', 'mandatoryOutstanding',
];

/**
 * The "can this be completed, and if not why not" block (VerificationSummaryResponse).
 *
 * Sprint 6: was `summary ? { ...summary } : null` — a raw spread, justified in a comment by "the
 * summary's field set is still moving on the backend". It is not moving; it is a record with seven
 * fixed components. A raw spread also meant every OTHER normalizer's guarantees (null-for-absent,
 * validated dates, tolerated aliases) stopped at this object's edge.
 *
 * `completable` is triBool, not bool. Defaulting an unknown to FALSE merely greys out a button;
 * defaulting it to true would invite a notary to complete a verification the server will reject.
 * Neither is worth guessing when null renders honestly as "—".
 */
export function normalizeVerificationSummary(raw) {
  const s = obj(raw, null);
  if (!s) return null;
  const out = {
    bundleId: str(pick(s, ['bundleId']), null),
    verificationId: str(pick(s, ['verificationId']), null),
    status: str(pick(s, ['status']), null),
    completable: triBool(pick(s, ['completable'])),
    // How many items block completion (manualRequiredCount), and how many MANDATORY items are still
    // unanswered or failed. Null when absent — never 0, which reads as "nothing is blocking" and is
    // the exact opposite of "we do not know what is blocking".
    blocking: num(pick(s, ['blocking']), null),
    mandatoryOutstanding: num(pick(s, ['mandatoryOutstanding']), null),
    // The summary embeds its own copy of the progress counters.
    progress: obj(pick(s, ['progress']), null),
  };
  return withExtras(out, s, SUMMARY_CONSUMED);
}

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

    // Progress. Every field is guarded: a partial response must not crash a progress bar. Counts fall
    // back to what the checklist itself shows rather than to zero — 0/0 reads as "nothing to do",
    // which is a claim, where a derived count is at least consistent with the rows on screen.
    //
    // Sprint 6: this read `completed` and `percent`. VerificationProgressResponse has NEITHER. Its
    // actual components are:
    //
    //   total, passed, failed, notApplicable, manualRequired, remaining, checklistComplete
    //
    // So `completed` was always undefined -> the fallback ran every time, and `percent` was
    // unconditionally null. The fallback happened to be close to right, which is what made this
    // invisible: the bar moved, so nobody asked whether the server's own counters were being read.
    // They were not — six of the seven counters were dropped on the floor, including the two
    // (`failed`, `manualRequired`) that say a check did not merely go unanswered but FAILED.
    //
    // `completed` is now DERIVED from the server's own numbers (total - remaining) rather than
    // guessed at, and `percent` is computed here because no backend sends it. Both fall back to the
    // rows on screen only when the server sends no progress block at all.
    progress: (() => {
      const total = count(pick(progress, ['total']), checklist.length);
      const remaining = num(pick(progress, ['remaining']), null);
      const completed = remaining !== null
        ? Math.max(0, total - remaining)
        : count(pick(progress, ['completed']), checklist.filter((i) => i.decision !== null).length);
      return {
        total,
        completed,
        // null, not 0, when there is nothing to divide by: "0%" is a claim about an empty checklist,
        // and an empty checklist has no progress to report.
        percent: num(pick(progress, ['percent']), null) ?? (total > 0 ? Math.round((completed / total) * 100) : null),
        // The server's real counters, passed through. These are the ones that distinguish "not yet
        // reviewed" from "reviewed and FAILED" — a distinction the old shape could not express.
        remaining: remaining !== null ? count(remaining, 0) : Math.max(0, total - completed),
        passed: num(pick(progress, ['passed']), null),
        failed: num(pick(progress, ['failed']), null),
        notApplicable: num(pick(progress, ['notApplicable']), null),
        manualRequired: num(pick(progress, ['manualRequired']), null),
        checklistComplete: triBool(pick(progress, ['checklistComplete'])),
      };
    })(),

    summary: normalizeVerificationSummary(summary),

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
