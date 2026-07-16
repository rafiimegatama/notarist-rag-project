// Lightweight request logger (Sprint 4, Task 9). DEVELOPMENT ONLY.
//
// Gated on `__DEV__`, which Metro replaces with a literal `false` in a release build — the dead
// branch is then dropped, so this costs nothing in production and cannot be switched on by config in
// a shipped app. That is intentional: a logger that a runtime flag can enable in production is a
// data-disclosure bug waiting for someone to flip it while debugging against real data.
//
// Everything logged passes through redact.js first: headers are blocklisted, bodies are reduced to
// their shape, and token-shaped strings are scrubbed wherever they appear. See redact.js for why
// bodies are shape-only rather than filtered by key name.

import { redactUrl, redactHeaders, describeShape } from './redact';
import { normalizeError } from './errors';

// Ring buffer of recent entries, for a developer inspecting the last N calls (e.g. from the dev
// Playground). Bounded so a long session cannot grow it without limit.
const MAX_ENTRIES = 50;
const entries = [];

function record(entry) {
  entries.push(entry);
  if (entries.length > MAX_ENTRIES) entries.shift();
}

/** Recent request log entries, newest last. Redacted at write time — safe to render. */
export function getRequestLog() {
  return entries.slice();
}

export function clearRequestLog() {
  entries.length = 0;
}

const statusColor = (status) => {
  if (!status) return '⚪';
  if (status >= 500) return '🔴';
  if (status >= 400) return '🟠';
  if (status >= 300) return '🟡';
  return '🟢';
};

/**
 * Install request/response/error logging on an axios instance.
 * No-op unless __DEV__. Never throws — a logger must not be able to fail a request.
 */
export function installLogger(instance) {
  if (typeof __DEV__ === 'undefined' || !__DEV__) return;

  instance.interceptors.request.use(
    (config) => {
      try {
        config.__startedAt = Date.now();
        const method = String(config.method || '?').toUpperCase();
        console.log(
          `→ ${method} ${redactUrl(config.url)}`,
          { headers: redactHeaders(config.headers), body: describeShape(config.data) }
        );
      } catch (_) {
        // A logging failure must never surface as a request failure.
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  instance.interceptors.response.use(
    (response) => {
      try {
        const cfg = response.config || {};
        const latencyMs = cfg.__startedAt ? Date.now() - cfg.__startedAt : null;
        const method = String(cfg.method || '?').toUpperCase();
        const url = redactUrl(cfg.url);
        record({ method, url, status: response.status, latencyMs, at: Date.now() });
        console.log(
          `${statusColor(response.status)} ${method} ${url} → ${response.status} (${latencyMs}ms)`,
          { body: describeShape(response.data) }
        );
      } catch (_) {
        /* never fail a response because logging broke */
      }
      return response;
    },
    (error) => {
      try {
        const cfg = (error && error.config) || {};
        const latencyMs = cfg.__startedAt ? Date.now() - cfg.__startedAt : null;
        const method = String(cfg.method || '?').toUpperCase();
        const url = redactUrl(cfg.url);
        const normalized = normalizeError(error);
        record({
          method, url, status: normalized.status, latencyMs, at: Date.now(), kind: normalized.kind,
        });
        console.log(
          `${statusColor(normalized.status)} ${method} ${url} → ${normalized.status || normalized.kind} (${latencyMs}ms)`,
          { kind: normalized.kind, diagnostic: normalized.diagnostic }
        );
      } catch (_) {
        /* swallow */
      }
      return Promise.reject(error);
    }
  );
}
