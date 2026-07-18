// Assistant SSE streaming client (Sprint 3) — the token-by-token half of api/assistant.js#askAssistant.
//
// askAssistant() waits for the whole answer and returns it in one piece. This module consumes the
// SAME pipeline's streaming endpoint (POST /assistant/ask/stream) and hands the screen each fragment
// as the model generates it, so a notary watches the answer arrive instead of staring at a spinner
// for the length of an LLM inference.
//
// ---------------------------------------------------------------------------------------------
// WHY XHR AND NOT fetch()/EventSource
//
//   * EventSource cannot POST. The endpoint is a POST (it carries the query + safety knobs in a
//     body), and the browser EventSource API is GET-only. Ruled out.
//   * fetch().body.getReader() — the clean streaming path on web — does not exist in React Native's
//     networking. react-native-web has it, bare RN does not, and this app ships to both.
//   * XMLHttpRequest exposes responseText incrementally (readyState LOADING / onprogress) on BOTH
//     react-native-web and native RN. It is the one transport that streams on every target, so it is
//     the one used here. If a platform does not stream, responseText simply fills in at the end and
//     every frame still parses — the answer arrives in one go instead of gradually, never wrong.
//
// This deliberately bypasses api/client's axios interceptors (there is no way to stream through them).
// The two things those interceptors give a request are reproduced here: the bearer token is attached
// from SecureStore exactly as the request interceptor does, and a non-2xx response is turned into the
// SAME ApiError the rest of the app switches on (api/errors.kindForStatus). What is NOT reproduced is
// silent 401 refresh — a stream that 401s reports UNAUTHORIZED and the screen falls back to
// askAssistant(), which DOES go through axios and so refreshes and retries. One real answer, no fake.
// ---------------------------------------------------------------------------------------------

import { getToken } from '../utils/tokenStore';
import { BASE_URL } from './client';
import { FEATURES } from '../constants/config';
import { requireEndpoint } from './_support';
import { ApiError, ErrorKind, kindForStatus, messageForKind } from './errors';

// SSE frames are separated by a blank line. Spring's SseEmitter writes "\n\n"; some proxies rewrite
// line endings to CRLF, so fresh text is normalised to "\n" before framing (see start()).
const FRAME_SEP = '\n\n';

/**
 * Parse one raw SSE frame into a structured event, or null if it carries no data line.
 *
 * A frame is the text between two blank lines, e.g.
 *   event:ANSWER_TOKEN
 *   data:{"eventType":"ANSWER_TOKEN","data":"Akta ","traceId":"…","sequence":0,"timestampMs":…}
 *
 * The `data:` payload is the whole SseEvent record (ResponseStreamer#emit serialises the event
 * itself), so eventType is read from the parsed JSON — the `event:` line is redundant and ignored.
 * Multiple `data:` lines, per the SSE spec, join with "\n" before parsing.
 *
 * Exported for unit testing: the framing/parsing is the part most likely to break against a real
 * server, and it is a pure function of a string, so it can be exercised without a socket.
 */
export function parseSseFrame(frame) {
  const dataLines = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('data:')) {
      // A single leading space after the colon is optional per spec; strip at most one.
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (!dataLines.length) return null;

  let envelope;
  try {
    envelope = JSON.parse(dataLines.join('\n'));
  } catch (_) {
    // A frame we cannot parse is dropped rather than crashing the stream — a corrupt token must not
    // lose the tokens on either side of it. The server owns this shape; if it drifts, the fallback
    // to askAssistant() still delivers the real answer.
    return null;
  }

  const type = envelope.eventType;
  const raw = envelope.data;

  switch (type) {
    case 'ANSWER_TOKEN':
      return { type, token: typeof raw === 'string' ? raw : '' };
    case 'CITATION':
      return { type, citation: safeJson(raw) };
    case 'CONFIDENCE':
      return { type, ...parseConfidence(raw) };
    case 'WARNING':
      return { type, warning: String(raw ?? '') };
    case 'FOLLOW_UP':
      return { type, question: String(raw ?? '') };
    case 'DONE':
      return { type, traceId: String(raw ?? '') };
    case 'ERROR':
      return { type, message: String(raw ?? '') };
    default:
      return { type: type || 'UNKNOWN', raw };
  }
}

// CONFIDENCE data is "LEVEL | score=0.87" (ResponseStreamer#emitTail). Parsed into the same shape the
// non-streaming path exposes — a level enum string and a finite score — so the badge renders one way.
//
// The score is accepted with either "." or "," as the decimal separator. The server formats it with
// String.format("%.2f", …), which follows the JVM's DEFAULT locale — and in a comma-decimal locale
// (id_ID, the locale this platform is most likely to be deployed under) that emits "score=0,87".
// Matching only [\d.] took the "0" and silently reported every confidence as 0.00 — a wrong number on
// a grounding badge, which is worse than no number. The non-streaming path is unaffected (JSON floats
// are locale-free); this is purely an SSE-wire concern.
function parseConfidence(raw) {
  const text = String(raw ?? '');
  const [levelPart, scorePart] = text.split('|');
  const level = (levelPart || '').trim() || null;
  const match = /score\s*=\s*(\d+(?:[.,]\d+)?)/.exec(scorePart || '');
  const score = match ? Number(match[1].replace(',', '.')) : null;
  return { level, score: Number.isFinite(score) ? score : null };
}

function safeJson(raw) {
  if (raw && typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

/**
 * Stream an assistant answer. Returns a handle with abort() — the "Stop Generation" control — that
 * severs the connection; the backend cancels the in-flight inference when the socket drops
 * (AssistantController wires onError/onCompletion to StreamingCancellationManager).
 *
 * @param {string} query
 * @param {string} sessionId
 * @param {Object} options   same knobs as askAssistant (safetyMode, contextTokenBudget, …)
 * @param {Object} handlers  { onToken, onCitation, onConfidence, onWarning, onFollowUp, onDone, onError }
 *                           Every handler is optional. onError receives an ApiError. onDone fires once,
 *                           on natural completion only — never after abort() and never after onError.
 * @returns {{ abort: () => void }}
 */
export function streamAssistant(query, sessionId, options = {}, handlers = {}) {
  // Mirrors askAssistant: there is no mock path and there must not be one — a fabricated legal answer
  // is the most harmful thing this app could render. A disabled flag throws UNAVAILABLE like any gate.
  requireEndpoint(FEATURES.assistantStreamEndpoint && FEATURES.assistantEndpoint, 'assistant/ask/stream');

  const xhr = new XMLHttpRequest();
  let seenLength = 0;   // chars of responseText already folded into `buffer`
  let buffer = '';
  let aborted = false;
  let settled = false;  // onDone or onError has fired — guards against double-settling on abort/load

  const fail = (kind, diagnostic) => {
    if (settled || aborted) return;
    settled = true;
    handlers.onError?.(new ApiError({ kind, status: null, message: messageForKind(kind), diagnostic, retryable: false }));
  };

  // DONE settles the stream the moment its FRAME arrives, not at transport close. Verified against
  // the running backend (RC acceptance): the server emits every frame and then drops the connection
  // WITHOUT the chunked-encoding terminator (curl exits 18), so XHR fires onerror, not onload. With
  // settlement tied to onload, every completed streamed answer grew a spurious "cannot reach server"
  // banner. DONE is the protocol's terminal event; anything the transport does after it is noise.
  const settleDone = () => {
    if (settled || aborted) return;
    settled = true;
    handlers.onDone?.();
  };

  const drainFrames = () => {
    let idx;
    while ((idx = buffer.indexOf(FRAME_SEP)) !== -1) {
      const frame = buffer.slice(0, idx);
      buffer = buffer.slice(idx + FRAME_SEP.length);
      dispatch(parseSseFrame(frame), handlers, settleDone);
    }
  };

  const pump = () => {
    if (aborted || settled) return;
    const text = xhr.responseText || '';
    if (text.length <= seenLength) return;
    // Normalise CRLF from proxies so framing on "\n\n" holds regardless of who touched the stream.
    buffer += text.slice(seenLength).replace(/\r\n/g, '\n');
    seenLength = text.length;
    drainFrames();
  };

  xhr.onreadystatechange = () => {
    // LOADING (3) is where native RN surfaces incremental responseText.
    if (xhr.readyState === XMLHttpRequest.LOADING) pump();
  };
  // onprogress is where react-native-web / browsers surface it. Both are wired; pump() is idempotent
  // via seenLength, so being called by both handlers streams the text exactly once.
  xhr.onprogress = pump;

  xhr.onload = () => {
    if (aborted) return;
    if (xhr.status >= 400 && !settled) {
      fail(kindForStatus(xhr.status), `assistant/ask/stream -> HTTP ${xhr.status}`);
      return;
    }
    pump();
    // Flush a trailing frame the server did not terminate with a blank line.
    if (buffer.trim()) dispatch(parseSseFrame(buffer), handlers, settleDone);
    buffer = '';
    // Fallback for a stream that ended without a DONE frame; a DONE already settled above.
    settleDone();
  };

  // A transport error AFTER the final frames is teardown noise, not a failure — drain what arrived
  // first, then let `settled` decide (see settleDone). A genuinely broken stream still fails here.
  xhr.onerror = () => {
    pump();
    if (buffer.trim()) { dispatch(parseSseFrame(buffer), handlers, settleDone); buffer = ''; }
    fail(ErrorKind.UNREACHABLE, 'assistant/ask/stream — XHR transport error');
  };
  xhr.ontimeout = () => fail(ErrorKind.TIMEOUT, 'assistant/ask/stream — XHR timeout');

  // The backend caps the stream at 60s; give the client a little more headroom so the server's own
  // timeout (which sends a clean completion) wins the race over a blunt client-side abort.
  xhr.timeout = 65000;

  (async () => {
    let token = null;
    try {
      token = await getToken('jwt_token');
    } catch (_) {
      // No secure store (e.g. web without it) — proceed unauthenticated; the server answers 401 and
      // the screen falls back to askAssistant().
    }
    if (aborted) return;

    try {
      xhr.open('POST', `${BASE_URL}/assistant/ask/stream`);
      xhr.setRequestHeader('Content-Type', 'application/json');
      xhr.setRequestHeader('Accept', 'text/event-stream');
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      xhr.send(
        JSON.stringify({
          rawQuery: query,
          sessionId,
          safetyMode: options.safetyMode ?? 'STRICT',
          contextTokenBudget: options.contextTokenBudget ?? 3072,
          maxClassificationLevel: options.maxClassificationLevel ?? null,
          documentTypeFilter: options.documentTypeFilter ?? null,
          maxResults: options.maxResults ?? 10,
        }),
      );
    } catch (err) {
      fail(ErrorKind.UNKNOWN, `assistant/ask/stream — send failed: ${err?.message || err}`);
    }
  })();

  return {
    abort() {
      if (aborted || settled) return;
      aborted = true;
      try {
        xhr.abort();
      } catch (_) {
        /* already closed */
      }
    },
  };
}

function dispatch(event, handlers, settleDone) {
  if (!event) return;
  switch (event.type) {
    case 'ANSWER_TOKEN':
      if (event.token) handlers.onToken?.(event.token);
      break;
    case 'CITATION':
      if (event.citation) handlers.onCitation?.(event.citation);
      break;
    case 'CONFIDENCE':
      handlers.onConfidence?.({ level: event.level, score: event.score });
      break;
    case 'WARNING':
      if (event.warning) handlers.onWarning?.(event.warning);
      break;
    case 'FOLLOW_UP':
      if (event.question) handlers.onFollowUp?.(event.question);
      break;
    case 'ERROR':
      handlers.onError?.(new ApiError({
        kind: ErrorKind.SERVER,
        status: null,
        message: event.message || messageForKind(ErrorKind.SERVER),
        diagnostic: `assistant stream ERROR event: ${event.message}`,
        retryable: false,
      }));
      break;
    case 'DONE':
      // Terminal by protocol. Settling here — not at transport close — is what keeps the verified
      // server behavior (abrupt close after DONE, no chunked terminator) from surfacing as an error.
      settleDone?.();
      break;
    default:
      break; // unknown events are ignored.
  }
}
