// Centralized API error normalization (Sprint 4, Task 1).
//
// Every failure that leaves the api/* layer — HTTP status, dropped socket, timeout — arrives at the
// UI as one shape: ApiError. Screens and state slices switch on `kind`, never on `err.response.
// status`, so a status code is classified in exactly one place instead of at every call site.
//
// Two fields, deliberately separate:
//   message    — user-facing Indonesian copy. Safe to render. Never contains a URL, body or header.
//   diagnostic — developer detail (method, path, status). Shown only where a developer would look;
//                <ErrorState> renders it behind __DEV__. Never contains the JWT (see redact.js).
//
// `retryable` is a property of the *error*, not of the request. It says "this failure could plausibly
// succeed if tried again"; whether it is *safe* to retry automatically depends on the HTTP method and
// is decided in retry.js. A 503 on a POST is retryable-in-principle and still must not be replayed.

export const ErrorKind = {
  UNAUTHORIZED: 'unauthorized',   // 401 — session gone/invalid
  FORBIDDEN: 'forbidden',         // 403 — authenticated but not allowed
  NOT_FOUND: 'notFound',          // 404 — route or resource absent
  CONFLICT: 'conflict',           // 409 — state collision (e.g. concurrent edit)
  // 412 — a precondition failed: the client sent If-Match/version and the server's copy has moved
  // on. Added in Sprint 5 (Task 7). Distinct from 409: a conflict means "your change collides with
  // the current state", a precondition failure means "the state you based your change on is stale".
  // Both roll an optimistic update back; only this one is certain the client's snapshot is old.
  PRECONDITION_FAILED: 'preconditionFailed',
  VALIDATION: 'validation',       // 422 — payload rejected, per-field detail may exist
  RATE_LIMITED: 'rateLimited',    // 429 — too many requests, may carry Retry-After
  SERVER: 'server',               // 500 — backend fault
  UNAVAILABLE: 'unavailable',     // 503 — backend up but not serving (deploy, overload)
  OFFLINE: 'offline',             // no network interface / request never left the device
  TIMEOUT: 'timeout',             // request sent, no answer within the deadline
  UNREACHABLE: 'unreachable',     // network exists but host/DNS refused or unresolvable
  UNKNOWN: 'unknown',             // anything unclassified — never silently swallowed
};

// User-facing copy. Indonesian, matching the rest of the app. These are read aloud by screen readers
// and shown to notaries, so they say what happened and what to do — not a status code.
const MESSAGES = {
  [ErrorKind.UNAUTHORIZED]: 'Sesi Anda telah berakhir. Silakan masuk kembali.',
  [ErrorKind.FORBIDDEN]: 'Anda tidak memiliki akses ke data ini.',
  [ErrorKind.NOT_FOUND]: 'Data yang diminta tidak ditemukan.',
  [ErrorKind.CONFLICT]: 'Data telah berubah di perangkat lain. Muat ulang lalu coba lagi.',
  // Says what happened, what was done about it, and what to do next — a notary needs to know their
  // change did NOT land, not merely that something went wrong.
  [ErrorKind.PRECONDITION_FAILED]: 'Data di server sudah diperbarui. Perubahan Anda dibatalkan agar tidak menimpa data terbaru. Muat ulang lalu coba lagi.',
  [ErrorKind.VALIDATION]: 'Data yang dikirim tidak valid. Periksa kembali isian Anda.',
  [ErrorKind.RATE_LIMITED]: 'Terlalu banyak permintaan. Mohon tunggu sebentar.',
  [ErrorKind.SERVER]: 'Terjadi gangguan pada server. Coba lagi nanti.',
  [ErrorKind.UNAVAILABLE]: 'Layanan sedang tidak tersedia. Coba lagi beberapa saat lagi.',
  [ErrorKind.OFFLINE]: 'Anda sedang offline. Periksa koneksi internet Anda.',
  [ErrorKind.TIMEOUT]: 'Permintaan memakan waktu terlalu lama. Coba lagi.',
  [ErrorKind.UNREACHABLE]: 'Tidak dapat menghubungi server. Periksa koneksi Anda.',
  [ErrorKind.UNKNOWN]: 'Terjadi kesalahan yang tidak diketahui.',
};

// Failures worth retrying: the request may succeed unchanged on a later attempt. 4xx are excluded —
// the same request will be rejected the same way — except 429, which is explicitly "later, not never".
const RETRYABLE = {
  [ErrorKind.RATE_LIMITED]: true,
  [ErrorKind.SERVER]: true,
  [ErrorKind.UNAVAILABLE]: true,
  [ErrorKind.OFFLINE]: true,
  [ErrorKind.TIMEOUT]: true,
  [ErrorKind.UNREACHABLE]: true,
};

const STATUS_KIND = {
  401: ErrorKind.UNAUTHORIZED,
  403: ErrorKind.FORBIDDEN,
  404: ErrorKind.NOT_FOUND,
  409: ErrorKind.CONFLICT,
  412: ErrorKind.PRECONDITION_FAILED,
  422: ErrorKind.VALIDATION,
  429: ErrorKind.RATE_LIMITED,
  500: ErrorKind.SERVER,
  503: ErrorKind.UNAVAILABLE,
};

export class ApiError extends Error {
  constructor({ kind, status, message, diagnostic, retryable, retryAfterMs, fieldErrors, cause }) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status || null;
    this.diagnostic = diagnostic || '';
    this.retryable = !!retryable;
    this.retryAfterMs = retryAfterMs || null;
    this.fieldErrors = fieldErrors || null;
    this.cause = cause;
    // Preserved so the pre-existing `isOffline`/`is404` predicates in _support.js keep working on a
    // normalized error. Removing these would silently change every context's offline detection.
    this.response = cause && cause.response;
    this.request = cause && cause.request;
  }
}

export function isApiError(e) {
  return !!e && e.name === 'ApiError';
}

// Distinguishes "no network at all" from "network present, host unreachable". axios collapses both
// into a bare `Network Error`, so the transport code is the only signal available.
function classifyNetworkError(error) {
  const code = error && error.code;
  if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') return ErrorKind.TIMEOUT;
  if (code === 'ERR_NETWORK') return ErrorKind.UNREACHABLE;
  if (code === 'ENOTFOUND' || code === 'ECONNREFUSED' || code === 'EHOSTUNREACH') return ErrorKind.UNREACHABLE;
  // A request that was created but never answered, with no code to explain why, is treated as the
  // device being offline: the most common cause on mobile, and the most actionable message.
  return ErrorKind.OFFLINE;
}

// Retry-After is seconds or an HTTP-date. Returns ms, or null when absent/unparseable.
function parseRetryAfter(headers) {
  const raw = headers && (headers['retry-after'] || headers['Retry-After']);
  if (!raw) return null;
  const seconds = Number(raw);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(raw);
  if (Number.isFinite(date)) return Math.max(0, date - Date.now());
  return null;
}

// Pulls per-field validation detail out of a 422 body if the backend sent any. Shape-tolerant: the
// contract is not frozen yet, so this reads the two shapes Spring commonly emits and gives up
// quietly rather than throwing inside an error handler.
function extractFieldErrors(data) {
  if (!data || typeof data !== 'object') return null;
  if (Array.isArray(data.errors)) {
    const out = {};
    for (const e of data.errors) {
      if (e && e.field) out[e.field] = e.defaultMessage || e.message || 'Tidak valid';
    }
    return Object.keys(out).length ? out : null;
  }
  if (data.fieldErrors && typeof data.fieldErrors === 'object') return data.fieldErrors;
  return null;
}

// Builds the developer-facing string. Method + path + status only — never the body, never headers,
// so a token in a header or a debtor name in a payload cannot reach a log or an error panel.
function buildDiagnostic(error, status, kind) {
  const cfg = error && error.config;
  const method = cfg && cfg.method ? String(cfg.method).toUpperCase() : '?';
  const url = cfg && cfg.url ? String(cfg.url).split('?')[0] : '?';
  return status ? `${method} ${url} → ${status} (${kind})` : `${method} ${url} → ${kind}`;
}

/**
 * Normalize any thrown value into an ApiError. Total: never throws, always returns an ApiError.
 * Already-normalized errors pass through so the interceptor chain can call this more than once.
 */
export function normalizeError(error) {
  if (isApiError(error)) return error;

  const status = error && error.response && error.response.status;
  let kind;
  if (status) {
    kind = STATUS_KIND[status];
    if (!kind) {
      // Unmapped statuses still get a sensible bucket rather than falling to UNKNOWN: any 5xx is a
      // server fault the user can retry, any other 4xx is a client fault they cannot.
      if (status >= 500) kind = ErrorKind.SERVER;
      else if (status >= 400) kind = ErrorKind.UNKNOWN;
      else kind = ErrorKind.UNKNOWN;
    }
  } else if (error && (error.request || error.message === 'Network Error' || error.code)) {
    kind = classifyNetworkError(error);
  } else {
    kind = ErrorKind.UNKNOWN;
  }

  const data = error && error.response && error.response.data;
  return new ApiError({
    kind,
    status: status || null,
    message: MESSAGES[kind] || MESSAGES[ErrorKind.UNKNOWN],
    diagnostic: buildDiagnostic(error, status, kind),
    retryable: !!RETRYABLE[kind],
    retryAfterMs: status === 429 ? parseRetryAfter(error.response.headers) : null,
    fieldErrors: status === 422 ? extractFieldErrors(data) : null,
    cause: error,
  });
}

/** Convenience for screens: the message to render, for any thrown value. */
export function messageFor(error) {
  return normalizeError(error).message;
}
