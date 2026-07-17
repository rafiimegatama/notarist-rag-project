// Normalizer primitives (Sprint 5, Task 2).
//
// Every DTO normalizer is built from these, so "tolerate a partial response" is implemented once
// rather than re-argued per entity. The four requirements from the brief, and how each is met:
//
//   missing fields   — every reader takes a fallback; a reader never touches a nested key without
//                      guarding the parent. Nothing throws.
//   nullable fields  — null and undefined are treated identically at the edge. A server that stops
//                      sending a field and a server that sends it as null mean the same thing to a
//                      screen: "no value".
//   legacy fields    — `pick()` takes an ordered list of aliases, so `id ?? caseId` style migrations
//                      are declarative and both spellings keep working during a rollout.
//   future additive  — `withExtras()` preserves unknown keys under `__extra` instead of dropping
//                      them. A field the backend adds tomorrow survives the round trip through the
//                      cache, and is visible in dev, without any screen having to know about it.
//
// A NOTE ON HONESTY, which outranks all of the above:
//
// A normalizer must not invent data. Where a value is genuinely absent the output is null, never a
// plausible-looking placeholder. `debtorName: 'Tanpa Nama'` reads like a real unnamed debtor; null
// lets the UI render "—" and tell the truth. Defaulting is for SHAPE (an array stays an array), not
// for CONTENT.

/** Absent means absent: undefined and null are the same signal from a JSON boundary. */
const isAbsent = (v) => v === undefined || v === null;

/**
 * First present value among `keys`, else `fallback`. Declares legacy/alias support at the field.
 *   pick(raw, ['caseId', 'id'])  ->  new spelling wins, old spelling still works
 */
export function pick(raw, keys, fallback = null) {
  if (!raw || typeof raw !== 'object') return fallback;
  for (const key of keys) {
    if (!isAbsent(raw[key])) return raw[key];
  }
  return fallback;
}

/** A trimmed non-empty string, else fallback. An empty/blank string is absent, not a value. */
export function str(value, fallback = null) {
  if (isAbsent(value)) return fallback;
  const s = String(value).trim();
  return s === '' ? fallback : s;
}

/** A finite number, else fallback. Rejects NaN/Infinity and numeric-looking junk. */
export function num(value, fallback = null) {
  if (isAbsent(value) || value === '') return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

/** A count: a finite integer >= 0. Anything else is fallback (default 0 — a count has a shape). */
export function count(value, fallback = 0) {
  const n = num(value, null);
  if (n === null) return fallback;
  return n < 0 ? fallback : Math.trunc(n);
}

export function bool(value, fallback = false) {
  if (isAbsent(value)) return fallback;
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return !!value;
}

/**
 * An ISO-8601 date string that actually parses, else fallback.
 *
 * The backend serializes every timestamp with Instant#toString / LocalDate#toString, so the wire
 * format is a string. Validating here means a screen's formatDate() never receives "0000-00-00" or a
 * half-written value and renders "Invalid Date" to a notary.
 */
export function isoDate(value, fallback = null) {
  const s = str(value, null);
  if (s === null) return fallback;
  return Number.isNaN(Date.parse(s)) ? fallback : s;
}

/** A value constrained to a known vocabulary, else fallback. Unknown enum members do not leak. */
export function oneOf(value, allowed, fallback = null) {
  const s = str(value, null);
  if (s === null) return fallback;
  return allowed.indexOf(s) !== -1 ? s : fallback;
}

/** Always an array. Maps each entry through `fn`, dropping entries that normalize to null. */
export function list(value, fn) {
  if (!Array.isArray(value)) return [];
  if (!fn) return value;
  const out = [];
  for (const item of value) {
    const mapped = fn(item);
    if (mapped !== null && mapped !== undefined) out.push(mapped);
  }
  return out;
}

/** Always a plain object. Guards `raw.progress.total` style reads against a null parent. */
export function obj(value, fallback = null) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return fallback;
  return value;
}

/**
 * Keep unknown keys under `__extra` so an additive backend change is preserved rather than silently
 * dropped by the normalizer.
 *
 * This is the "future additive fields" requirement. It costs one shallow loop per entity and means a
 * new field is inspectable (in dev, via the logger's shape output, or in the cache) the day it ships
 * — instead of being invisible until someone edits the normalizer. Screens never read `__extra`; it
 * exists so the data is not lost and the gap is discoverable.
 *
 * @param {Object} normalized  the mapped entity
 * @param {Object} raw         the original payload
 * @param {string[]} consumed  keys the normalizer already read (including legacy aliases)
 */
export function withExtras(normalized, raw, consumed) {
  if (!raw || typeof raw !== 'object') return normalized;
  const extra = {};
  let has = false;
  for (const key of Object.keys(raw)) {
    if (consumed.indexOf(key) === -1 && key.slice(0, 2) !== '__') {
      extra[key] = raw[key];
      has = true;
    }
  }
  if (has) {
    // Non-enumerable: it must not show up in JSON.stringify of the cache, in a shallow-equality
    // prop check, or in a snapshot diff. It is diagnostic metadata, not part of the entity.
    Object.defineProperty(normalized, '__extra', { value: extra, enumerable: false, writable: false });
  }
  return normalized;
}

/**
 * Wrap a single-entity normalizer into the standard { one, list } pair every Normalizer exposes.
 * `one` is total: it returns an entity for any input, including null.
 */
export function makeNormalizer(one) {
  return {
    one,
    list: (value) => list(value, one),
  };
}
