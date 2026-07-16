// Optimistic mutation + conflict resolution + cache invalidation (Sprint 5, Tasks 3, 4, 7).
//
// One helper, because the three tasks are the same moment in time:
//
//   apply optimistic state -> call the server -> either invalidate the caches the write touched,
//                                                or roll the state back and explain why.
//
// ---------------------------------------------------------------------------------------------
// WHY ROLLBACK IS NOT OPTIONAL HERE
//
// This app writes verification decisions, QC checklist items, OCR corrections and case-status
// transitions on notarial documents. An optimistic update that fails and does NOT roll back leaves a
// notary looking at a screen that says their approval landed when it did not. That is worse than no
// optimistic update at all: the UI would be lying about a legal act. So rollback runs on EVERY
// failure — 409/412/422 by name in the brief, but also 500s, timeouts and offline. There is no
// failure mode where keeping the optimistic value is the safe choice.
//
// "Never overwrite newer server state" (Task 7) is enforced by direction: on conflict we discard the
// LOCAL value and keep the server's. The client never wins a disagreement about a document's state.
//
// NOTE ON RETRY: mutations are never retried automatically — api/retry.js is GET-only for exactly
// this reason. A rolled-back mutation is re-attempted by the USER, deliberately, after they have
// seen why it failed.
// ---------------------------------------------------------------------------------------------

import { normalizeError, ErrorKind } from '../api/errors';
import * as cache from './cache';

/** Failures that mean "the server's copy is authoritative and yours is stale". */
const CONFLICT_KINDS = [
  ErrorKind.CONFLICT,             // 409
  ErrorKind.PRECONDITION_FAILED,  // 412
  ErrorKind.VALIDATION,           // 422
];

export function isConflict(error) {
  return CONFLICT_KINDS.indexOf(normalizeError(error).kind) !== -1;
}

/**
 * Which cache entries a given mutation invalidates (Task 4: "refresh affected queries only").
 *
 * The map is explicit rather than clever: a wildcard "refresh everything" would be one line and
 * would also re-fetch the case list every time a single OCR field is corrected. Naming the affected
 * keys per mutation is the whole requirement, and it keeps the blast radius reviewable.
 *
 * Read as: this write changed these things, so only these caches are now wrong.
 */
export const INVALIDATES = {
  // A verification decision changes the bundle's progress and can advance the case's state, which
  // the dashboard counters and the reminder queue both read.
  verification: [cache.CacheKeys.DASHBOARD, cache.CacheKeys.CASE_LIST, cache.CacheKeys.REMINDERS],
  // A checklist item is a verification decision at finer grain — same blast radius.
  checklist: [cache.CacheKeys.DASHBOARD, cache.CacheKeys.CASE_LIST, cache.CacheKeys.REMINDERS],
  // An OCR field correction does NOT move the case out of its state on its own; it changes the
  // document under review. Nothing cached at list level depends on a single field's value, so this
  // deliberately invalidates nothing — the narrow answer, not the safe-looking broad one.
  ocrField: [],
  // A status transition is the one thing every list-level counter is derived from.
  caseStatus: [cache.CacheKeys.DASHBOARD, cache.CacheKeys.CASE_LIST, cache.CacheKeys.REMINDERS],
  // A timeline entry is append-only and case-scoped; no cached list shows it.
  timelineAppend: [],
  // Deleting a conversation changes only the conversation list.
  conversation: [cache.CacheKeys.CONVERSATIONS],
};

/**
 * Run a mutation optimistically.
 *
 * @param {Object}   spec
 * @param {Function} spec.apply     () => snapshot   — apply the optimistic state, RETURN the value
 *                                                     needed to undo it. Called synchronously.
 * @param {Function} spec.rollback  (snapshot) => void — restore. Must be total; it runs on failure.
 * @param {Function} spec.commit    () => Promise<any> — the server call.
 * @param {Function} [spec.settle]  (serverResult) => void — reconcile with what the server returned.
 * @param {string[]} [spec.invalidates] — cache keys to drop on success (see INVALIDATES).
 * @param {Function} [spec.onConflict] (ApiError) => void — conflict-specific hook (e.g. force reload).
 *
 * @returns {Promise<{ ok: boolean, data?: any, error?: ApiError, rolledBack?: boolean }>}
 *          Resolves rather than throws: an optimistic mutation's failure is a UI state, not an
 *          exceptional condition, and every caller has to handle it anyway.
 */
export async function optimistic({ apply, rollback, commit, settle, invalidates = [], onConflict }) {
  const snapshot = apply();

  try {
    const result = await commit();

    // Success: drop only the caches this write actually invalidated, so the next read refetches
    // them and everything else keeps serving from cache (Task 4 — no full refresh).
    await invalidateKeys(invalidates);

    // The server's answer supersedes the optimistic guess. Even on success the two can differ: a
    // status transition may land the case in a different state than the client predicted, and the
    // server is right.
    if (settle) settle(result);

    return { ok: true, data: result };
  } catch (err) {
    const error = normalizeError(err);

    // Unconditional. See the header: there is no failure where keeping the optimistic value is safe.
    rollback(snapshot);

    if (isConflict(error) && onConflict) {
      // The caller usually refetches here. The conflict itself already proved our snapshot is stale,
      // so the cache holding it is stale too — drop it before anyone reads it again.
      await invalidateKeys(invalidates);
      onConflict(error);
    }

    return { ok: false, error, rolledBack: true };
  }
}

/** Drop specific cache entries. Best-effort: a cache that will not clear must not fail a mutation
 *  that already succeeded on the server. */
async function invalidateKeys(keys) {
  if (!keys || !keys.length) return;
  for (const key of keys) {
    try {
      await cache.remove(key);
    } catch (_) {
      /* a stale cache entry is a slower next read, not a broken write */
    }
  }
}

export { invalidateKeys };
