// Automatic retry with exponential backoff (Sprint 4, Task 2).
//
// GET ONLY. This is the whole design constraint and it is not a stylistic preference:
//
// A GET is safe to replay because it does not change server state. POST/PUT/PATCH/DELETE are not.
// This app submits verification decisions, deletes conversations and (soon) generates notarial
// documents. A "timeout" does not mean the server did not receive the request — it means we did not
// hear the answer. Replaying a POST after a timeout is how one approval becomes two. So the method
// gate below is absolute and deliberately not configurable per-call: there is no `retry: true`
// escape hatch to reach for under deadline pressure. When the backend ships idempotency keys, an
// explicit opt-in can be added here — with the key, not without it.
//
// DELETE is idempotent in the RFC sense and still excluded: replaying it cannot corrupt state, but
// it turns "deleted, then recreated elsewhere" into a surprise second delete. Not worth the edge.

import { normalizeError, ErrorKind } from './errors';

export const RETRY_CONFIG = {
  maxAttempts: 3,      // 1 initial + 2 retries
  baseDelayMs: 400,
  maxDelayMs: 5000,
  jitterRatio: 0.25,   // ±25%, so N clients retrying a recovering server do not resynchronize
};

const RETRYABLE_METHODS = ['get', 'head', 'options'];

// Kinds worth a second attempt. 429 included and honoured via Retry-After; 4xx otherwise excluded —
// the same request gets the same rejection, so a retry only adds latency before the same error.
const RETRYABLE_KINDS = [
  ErrorKind.OFFLINE,
  ErrorKind.TIMEOUT,
  ErrorKind.UNREACHABLE,
  ErrorKind.SERVER,
  ErrorKind.UNAVAILABLE,
  ErrorKind.RATE_LIMITED,
];

function isRetryableMethod(config) {
  const method = config && config.method ? String(config.method).toLowerCase() : '';
  return RETRYABLE_METHODS.indexOf(method) !== -1;
}

// The auth refresh flow owns its own 401 recovery in client.js. Retrying /auth/* underneath it would
// race that logic and could hammer a login endpoint with a bad credential — leave it alone entirely.
function isAuthPath(config) {
  const url = config && config.url ? String(config.url) : '';
  return url.indexOf('/auth/') !== -1;
}

/**
 * Exponential backoff with symmetric jitter, clamped. Attempt is 1-based.
 *
 * The clamp is applied AFTER jitter, and that ordering is the fix for a real bug: clamping first and
 * then adding ±jitterRatio let the result overshoot maxDelayMs by up to 25% (a "5s max" returning
 * 6.25s). It only showed up about half the time, because whether it breached the cap depended on the
 * sign of a random number — a flaky test hiding a real defect. Clamping last makes maxDelayMs mean
 * what it says on every draw.
 */
export function backoffDelay(attempt, config = RETRY_CONFIG) {
  const exponential = config.baseDelayMs * Math.pow(2, attempt - 1);
  const capped = Math.min(exponential, config.maxDelayMs);
  const jitter = capped * config.jitterRatio * (Math.random() * 2 - 1);
  const jittered = capped + jitter;
  return Math.max(0, Math.min(Math.round(jittered), config.maxDelayMs));
}

/**
 * Decide whether a failed request gets another attempt, and how long to wait first.
 * Exported for the validation/test path — the method gate deserves to be assertable in isolation.
 *
 * @returns {{ retry: boolean, delayMs: number, reason: string }}
 */
export function planRetry(error, config = RETRY_CONFIG) {
  const original = error && error.config;
  if (!original) return { retry: false, delayMs: 0, reason: 'no request config' };
  if (isAuthPath(original)) return { retry: false, delayMs: 0, reason: 'auth path owns its recovery' };
  if (!isRetryableMethod(original)) {
    return { retry: false, delayMs: 0, reason: `method ${String(original.method).toUpperCase()} is not replay-safe` };
  }

  const attempt = original.__retryAttempt || 0; // attempts already made after the first
  if (attempt >= config.maxAttempts - 1) {
    return { retry: false, delayMs: 0, reason: `exhausted after ${config.maxAttempts} attempts` };
  }

  const normalized = normalizeError(error);
  if (RETRYABLE_KINDS.indexOf(normalized.kind) === -1) {
    return { retry: false, delayMs: 0, reason: `${normalized.kind} is not retryable` };
  }

  // A server that says "wait N" is obeyed rather than guessed at; backoff is only the fallback.
  const delayMs = normalized.retryAfterMs != null
    ? Math.min(normalized.retryAfterMs, config.maxDelayMs)
    : backoffDelay(attempt + 1, config);

  return { retry: true, delayMs, reason: normalized.kind };
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Install GET-only retry on an axios instance. Registered as a response interceptor; re-dispatches
 * through the same instance so the auth and logging interceptors still apply to every attempt.
 *
 * @param {import('axios').AxiosInstance} instance
 * @param {(info:{url:string,attempt:number,delayMs:number,reason:string}) => void} [onRetry]
 */
export function installRetry(instance, onRetry) {
  instance.interceptors.response.use(
    (response) => response,
    async (error) => {
      const plan = planRetry(error);
      if (!plan.retry) return Promise.reject(error);

      const original = error.config;
      original.__retryAttempt = (original.__retryAttempt || 0) + 1;

      if (onRetry) {
        onRetry({
          url: original.url,
          attempt: original.__retryAttempt,
          delayMs: plan.delayMs,
          reason: plan.reason,
        });
      }

      await sleep(plan.delayMs);
      return instance(original);
    }
  );
}
