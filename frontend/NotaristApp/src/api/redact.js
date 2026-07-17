// Redaction for anything that could reach a log line (Sprint 4, Task 9).
//
// This module exists because the logger's job — "show me the request" — and the app's duty of care
// are in direct tension. This app moves notarial data: debtor names, NIK, NPWP, bank details. None
// of it belongs in a console, and neither does the bearer token.
//
// The rule here is DENY BY DEFAULT for values, not allow-by-default with a blocklist. A blocklist
// only redacts the sensitive keys someone remembered to name; when the backend adds a field, the
// blocklist silently leaks it. So request/response *bodies* are never rendered — only their shape
// (keys and value types). A key name is metadata worth seeing; a value is data worth protecting.
//
// Header handling is the reverse: headers are a small, known set, so an explicit blocklist is exact.

const SENSITIVE_HEADERS = [
  'authorization',      // Bearer <JWT> — the one that matters most
  'cookie',
  'set-cookie',
  'x-api-key',
  'x-auth-token',
  'proxy-authorization',
  'refresh-token',
];

export const REDACTED = '[redacted]';

// Anything shaped like a JWT (three base64url segments) or a Bearer prefix, wherever it appears in a
// string. Defence in depth: even if a token reaches a message body or a URL, it does not reach a log.
const JWT_PATTERN = /\b(?:Bearer\s+)?[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b/g;
// Long opaque credentials in query strings: ?token=..., &access_token=..., ?apiKey=...
const QUERY_SECRET_PATTERN = /([?&](?:token|access_token|refresh_token|api_?key|password|secret)=)[^&\s]+/gi;

/** Scrub token-shaped substrings out of free text. Safe on any input; returns '' for non-strings. */
export function scrubString(value) {
  if (typeof value !== 'string') return '';
  return value.replace(JWT_PATTERN, REDACTED).replace(QUERY_SECRET_PATTERN, '$1' + REDACTED);
}

/** A URL with its query values stripped. Keys are kept — they explain the request; values may not. */
export function redactUrl(url) {
  if (typeof url !== 'string') return '';
  const [path, query] = url.split('?');
  if (!query) return scrubString(path);
  const keys = query
    .split('&')
    .map((pair) => pair.split('=')[0])
    .filter(Boolean);
  return `${scrubString(path)}?${keys.join('&')}=${REDACTED}`;
}

/** Headers with credential-bearing values replaced. Unknown headers keep their (scrubbed) value. */
export function redactHeaders(headers) {
  if (!headers || typeof headers !== 'object') return {};
  const out = {};
  for (const key of Object.keys(headers)) {
    if (SENSITIVE_HEADERS.indexOf(key.toLowerCase()) !== -1) {
      out[key] = REDACTED;
    } else {
      const v = headers[key];
      out[key] = typeof v === 'string' ? scrubString(v) : v;
    }
  }
  return out;
}

/**
 * Describe a payload without disclosing it: keys and value types only, never values.
 *
 *   { debtorName: 'Budi', amount: 5000 }  ->  { debtorName: 'string', amount: 'number' }
 *
 * This is what makes the logger safe to leave enabled in development on real data. Depth is capped
 * because a cyclic or deep graph must not turn a log call into a hang.
 */
export function describeShape(value, depth = 0) {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';
  if (depth > 3) return '…';

  if (Array.isArray(value)) {
    if (!value.length) return '[]';
    return [describeShape(value[0], depth + 1), `×${value.length}`];
  }
  const t = typeof value;
  if (t !== 'object') return t;

  // Not a plain object (Blob, FormData, Error…): name the type, disclose nothing.
  const proto = Object.getPrototypeOf(value);
  if (proto && proto !== Object.prototype) {
    return (value.constructor && value.constructor.name) || 'object';
  }
  const out = {};
  for (const key of Object.keys(value)) out[key] = describeShape(value[key], depth + 1);
  return out;
}
