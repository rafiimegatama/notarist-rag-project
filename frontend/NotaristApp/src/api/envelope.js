// Response envelope handling (Sprint 5, Task 1).
//
// Every backend response is an ApiResponse record:
//
//   { status: "SUCCESS" | "ERROR", meta, data, errorCode, errorMessage }
//
// (verified against backend/notarist-core/.../api/response/ApiResponse.java)
//
// Before this module every call site dug the payload out by hand — `response.data.data`,
// `response.data.data?.items ?? []`, `response.data?.data ?? {}` — three spellings of one idea, each
// with its own way of exploding. `response.data.data` throws a TypeError the moment a proxy, a
// gateway timeout page or a 204 hands back a body that is not the envelope. Sprint 5's rule is
// "never throw because a field is missing", so unwrapping happens once, here, defensively.
//
// This module deliberately does NOT normalize shapes — that is the normalizers' job. It only answers
// "what did the server actually send me", safely.

import { ApiError, ErrorKind, kindForStatus, messageForKind, isRetryableKind } from './errors';

/**
 * Pull the payload out of an axios response, whatever shape arrived.
 *
 * Tolerates, in order:
 *   - a proper envelope            -> returns `.data.data`
 *   - a bare payload (no envelope) -> returns `.data` (some endpoints may answer unwrapped)
 *   - null/204/garbage             -> returns the supplied fallback
 *
 * Never throws. Returns `fallback` (default null) rather than undefined so callers can rely on `??`.
 */
export function unwrap(response, fallback = null) {
  const body = response && response.data;
  if (body == null) return fallback;
  if (typeof body !== 'object') return fallback;

  // Envelope: has a `status` string and a `data` key. `data` may legitimately be null on success
  // (e.g. a mutation that returns nothing), which is why the key check matters, not truthiness.
  if (typeof body.status === 'string' && 'data' in body) {
    return body.data == null ? fallback : body.data;
  }
  // Not an envelope — treat the body itself as the payload.
  return body;
}

/**
 * True when the envelope reports a failure. The backend returns HTTP 200 with status:"ERROR" in
 * some paths (askAssistant and runSearch already checked for exactly this by hand), so a 2xx alone
 * does not mean success.
 */
export function isErrorEnvelope(response) {
  const body = response && response.data;
  return !!body && typeof body === 'object' && body.status === 'ERROR';
}

/**
 * Convert an error envelope into the same ApiError every other failure produces, so a 200-with-ERROR
 * and a 500 are indistinguishable to a screen. Without this, envelope errors bypassed the entire
 * Sprint-4 error layer: they were thrown as bare `new Error(...)` with no `kind`, so ErrorState could
 * not classify them and the retry layer could not reason about them.
 */
export function envelopeError(response) {
  const body = (response && response.data) || {};
  const status = (response && response.status) || null;
  // Classify from the HTTP status rather than asserting SERVER. Verified against the running backend
  // (Sprint 7): a missing case answers 404 + errorCode CASE_NOT_FOUND inside an ERROR envelope, and
  // this used to report it as a retryable server fault — telling the notary "coba lagi nanti" about
  // a record that does not exist, and handing retry.js a request it would replay until it gave up.
  //
  // A status BELOW 400 keeps the SERVER reading on purpose: that is the 200-with-status:"ERROR" case
  // isErrorEnvelope exists for, where HTTP carries no failure signal and the body is the only sign of
  // a backend fault. Only a real 4xx/5xx is allowed to speak for itself.
  const kind = status && status >= 400 ? kindForStatus(status) : ErrorKind.SERVER;
  const meta = body.meta;
  return new ApiError({
    kind,
    status,
    // errorMessage is server-authored and may be English/technical; the UI copy comes from `kind`.
    // It is kept as the diagnostic, not the message, for the same reason ErrorState hides it in prod.
    message: messageForKind(kind),
    diagnostic: `envelope ERROR${body.errorCode ? ' ' + body.errorCode : ''}: ${body.errorMessage || '(no message)'}`,
    retryable: isRetryableKind(kind),
    // Same support identifiers as the HTTP-error path, so a 200-with-status:ERROR is indistinguishable
    // to the error panel (Sprint 10).
    errorCode: body.errorCode ? String(body.errorCode) : null,
    correlationId: meta && meta.correlationId ? String(meta.correlationId) : null,
    cause: null,
  });
}

/**
 * Unwrap, but reject an error envelope first. Use for any call whose payload the UI will render.
 * @throws {ApiError} when the envelope reports ERROR
 */
export function unwrapOrThrow(response, fallback = null) {
  if (isErrorEnvelope(response)) throw envelopeError(response);
  return unwrap(response, fallback);
}

/**
 * Pull a list out of a payload that may be:
 *   - a bare array                  [...]
 *   - a paged wrapper               { items: [...] }
 *   - a Spring Page                 { content: [...] }
 *   - a named collection            { entries: [...] } / { checklist: [...] }
 *   - null / an object / nonsense   -> []
 *
 * The `key` hints are not speculative: TimelineResponse nests its rows under `entries` and
 * VerificationResponse under `checklist`, while /cases answers `{ items }`. One helper covers all of
 * them, so a shape change becomes a one-line edit rather than a crash.
 */
export function toList(payload, keys = ['items', 'content', 'entries', 'data']) {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== 'object') return [];
  for (const key of keys) {
    if (Array.isArray(payload[key])) return payload[key];
  }
  return [];
}
