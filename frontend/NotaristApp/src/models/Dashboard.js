// Dashboard counters (Sprint 5, Task 2).
//
// New in Sprint 5. The composition logic — why the dashboard reads /cases/statistics rather than
// /dashboard/summary — stays in api/dashboard.js, where the three-endpoint reasoning lives. This
// file owns only the SHAPE: given whatever those endpoints returned, produce the counter contract
// the cards read, without inventing a number.
//
// THE RULE THIS FILE EXISTS TO ENFORCE: null is not zero.
//
// A dashboard counter is a factual claim about a notary's workload. `0` says "nothing is overdue".
// `null` says "we could not find out". Defaulting a missing counter to 0 turns an outage into a
// clean bill of health — the single most dangerous thing this screen could do. So every counter
// here defaults to null, and only counters we genuinely computed carry a number.
//
// Verified against backend DashboardSummaryResponse.java + CaseStatisticsResponse.java.
import { pick, num, count, obj, str, withExtras, makeNormalizer } from './normalize';

export const COUNTER_KEYS = [
  'totalCase', 'draft', 'waitingVerification', 'waitingQc', 'waitingApproval',
  'readyToSend', 'overdueSkmht', 'reminderCount',
];

const CONSUMED = [...COUNTER_KEYS, 'averageProcessingSeconds', 'averageProcessingHuman', 'generatedAt'];

/**
 * Normalize the composed counter object (the shape api/dashboard.js builds).
 * Counters are `count()`-ed only when present; absent stays null.
 */
export function normalizeDashboard(raw = {}) {
  const source = obj(raw, {}) || {};
  const out = {};
  for (const key of COUNTER_KEYS) {
    const v = pick(source, [key]);
    // null/undefined -> null (unknown). A present value is coerced to a non-negative integer.
    out[key] = v === null ? null : count(v, null);
  }

  // Optional extras from DashboardSummaryResponse. Nullable on the backend too ("null when nothing
  // is closed yet"), so null here is the same statement, not a degradation.
  out.averageProcessingSeconds = num(pick(source, ['averageProcessingSeconds']), null);
  out.averageProcessingHuman = str(pick(source, ['averageProcessingHuman']), null);
  out.generatedAt = str(pick(source, ['generatedAt']), null);

  return withExtras(out, source, CONSUMED);
}

export const DashboardNormalizer = makeNormalizer(normalizeDashboard);

/**
 * Sum a set of CaseState buckets out of CaseStatisticsResponse#statusCounts.
 * Returns null — not 0 — when statusCounts is absent, so "no statistics" cannot masquerade as
 * "no cases". Used by api/dashboard.js.
 */
export function sumStates(statusCounts, states) {
  const map = obj(statusCounts, null);
  if (!map) return null;
  let total = 0;
  for (const s of states) total += count(map[s], 0);
  return total;
}

/** Total across every bucket, for the fallback path when /dashboard/summary is unavailable. */
export function sumAllStates(statusCounts) {
  const map = obj(statusCounts, null);
  if (!map) return null;
  return Object.keys(map).reduce((acc, k) => acc + count(map[k], 0), 0);
}
