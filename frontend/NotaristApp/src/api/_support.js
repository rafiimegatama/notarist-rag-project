import { ApiError, ErrorKind } from './errors';

// Shared helpers for the API layer. The `mock()` helper is how every module degrades gracefully
// while its backend endpoint is still missing: it resolves fake fixture data after a short delay
// (so screens exercise their real loading states) and tags the result with `__mock: true`, which
// screens read to show a "data contoh" banner. No module ever fabricates data silently.

export function isOffline(error) {
  // Since Sprint 4 the client normalizes every failure into an ApiError, so ask the classification
  // directly rather than re-deriving it from axios internals. TIMEOUT stays grouped with OFFLINE
  // here: this predicate answers "should the screen show the offline treatment?", and a request that
  // never came back is, to the user, the same situation as no signal. Callers needing the finer
  // distinction read `error.kind` (see api/errors.js).
  if (error && error.name === 'ApiError') {
    return error.kind === 'offline' || error.kind === 'unreachable' || error.kind === 'timeout';
  }
  // Fallback for a raw axios error (e.g. thrown by a module that bypasses the client instance):
  // network error (no response) vs an HTTP error response. Coerced to a real boolean — callers pass
  // this straight into setState, and the unwrapped expression returns the axios `request` object.
  return !!error && !error.response && !!(error.request || error.message === 'Network Error');
}

// True when the server answered "this route does not exist". Distinct from offline (no answer at
// all) and from 5xx (the route exists and broke), and the three want different handling:
//
//   404      -> the endpoint is not deployed in this environment. Fall back to the labelled mock,
//               exactly as if its FEATURES flag were still false. The banner tells the user.
//   offline  -> the network is gone. Surface the offline state; the data may be perfectly fine.
//   5xx      -> the backend is broken. Surface the error. Masking it with sample data would hide a
//               real outage behind plausible-looking numbers.
export function is404(error) {
  return error?.response?.status === 404;
}

// Resolve mock data after a realistic delay. `label` is for debugging only.
export function mock(data, { delay = 350, label } = {}) {
  return new Promise((resolve) => {
    setTimeout(() => {
      const tagged = Array.isArray(data)
        ? Object.assign([...data], { __mock: true, __mockLabel: label })
        : { ...data, __mock: true, __mockLabel: label };
      resolve(tagged);
    }, delay);
  });
}

// True when a value came from the mock path — screens use this to decide whether to show the banner.
export function isMock(value) {
  return !!value && value.__mock === true;
}

/**
 * Flag gate for endpoints that have NO mock fallback (Sprint 5, Task 10).
 *
 * Some endpoints cannot degrade to fixtures honestly. Search results are a ranked answer to a
 * specific query; document lists and ingestion jobs are per-tenant facts. Fabricating any of them
 * would be inventing legal-domain data, which the mock() path only ever does with a visible
 * "data contoh" banner attached. For these, "flag off" means "this feature is unavailable", and the
 * user is told so — it does not mean "show something plausible".
 *
 * Throws an ApiError so a disabled flag lands in exactly the same handling as a 503: <ErrorState>
 * already renders Indonesian copy for `unavailable`, and the retry layer already knows not to hammer
 * it. That is the point of routing it through the Sprint-4 error layer instead of a bespoke throw.
 *
 * @param {boolean} enabled  the FEATURES flag
 * @param {string}  label    endpoint name, for the dev diagnostic only
 */
export function requireEndpoint(enabled, label) {
  if (enabled) return;
  throw new ApiError({
    kind: ErrorKind.UNAVAILABLE,
    status: null,
    message: 'Fitur ini belum tersedia.',
    diagnostic: `FEATURES flag for "${label}" is false — request not sent`,
    // Not retryable: no amount of retrying flips a compile-time flag. Marking it retryable would
    // put a "Coba Lagi" button on a wall.
    retryable: false,
  });
}
