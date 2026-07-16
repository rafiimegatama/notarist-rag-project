// Case/bundle workflow timeline (Sprint 5, Task 2).
//
// New in Sprint 5. Before this, api/cases#getCaseTimeline returned `response.data.data ?? []` — the
// raw payload, unnormalized — and that is a live bug against the real endpoint:
//
//   TimelineResponse is an OBJECT: { timelineId, caseId, status, sealed, entryCount, entries[] }
//
// The screens expect an ARRAY of entries. Handing them the object means `.map is not a function`, or
// a silently empty timeline. `toTimelineEntries()` below is the fix: it accepts the object, a bare
// array (fixtures), or a paged wrapper, and always returns a list.
//
// Verified against backend/notarist-case/.../api/response/TimelineResponse.java +
// TimelineEntryResponse.java.
import { pick, str, num, isoDate, list, obj, withExtras, makeNormalizer } from './normalize';
import { toList } from '../api/envelope';

const ENTRY_CONSUMED = [
  'entryId', 'id', 'type', 'description', 'actorUserId', 'actorRole', 'sequence', 'occurredAt',
  'at', 'label', 'actor',
];

export function normalizeTimelineEntry(raw = {}) {
  const rawId = pick(raw, ['entryId', 'id']);
  const out = {
    id: rawId === null ? null : String(rawId),
    // The event type, e.g. CASE_CREATED. Kept raw: the timeline component maps it to a label, and
    // this layer must not put Indonesian UI copy on the wire's vocabulary.
    type: str(pick(raw, ['type']), null),
    description: str(pick(raw, ['description', 'label']), null),
    // A UUID. Named …Id so nothing renders it as a person's name; no user lookup endpoint exists.
    actorUserId: str(pick(raw, ['actorUserId']), null),
    actorRole: str(pick(raw, ['actorRole']), null),
    // Server-assigned ordering. Authoritative — two entries can share a timestamp.
    sequence: num(pick(raw, ['sequence']), null),
    occurredAt: isoDate(pick(raw, ['occurredAt', 'at']), null),
  };
  return withExtras(out, raw, ENTRY_CONSUMED);
}

export const TimelineEntryNormalizer = makeNormalizer(normalizeTimelineEntry);

const CONSUMED = ['timelineId', 'id', 'caseId', 'status', 'sealed', 'entryCount', 'createdAt', 'entries'];

/** The full timeline aggregate. */
export function normalizeTimeline(raw = {}) {
  const source = obj(raw, {}) || {};
  const rawId = pick(source, ['timelineId', 'id']);
  const rawCaseId = pick(source, ['caseId']);
  const entries = TimelineEntryNormalizer.list(toList(source, ['entries', 'items', 'content']));

  const out = {
    id: rawId === null ? null : String(rawId),
    caseId: rawCaseId === null ? null : String(rawCaseId),
    status: str(pick(source, ['status']), null),
    sealed: pick(source, ['sealed']) === true,
    // Trust the server's count when present, else the length we actually have. They differ when the
    // server paginates entries — in which case entryCount is the truth and entries.length is a page.
    entryCount: num(pick(source, ['entryCount']), null) ?? entries.length,
    createdAt: isoDate(pick(source, ['createdAt']), null),
    entries,
  };
  return withExtras(out, source, CONSUMED);
}

export const TimelineNormalizer = makeNormalizer(normalizeTimeline);

/**
 * The list every timeline screen actually wants, from whatever arrived: the TimelineResponse object,
 * a bare array of entries (fixtures), or a paged wrapper.
 *
 * Sorted by `sequence` when the server provides it — the timeline is an ordered audit record, and
 * rendering it out of order misrepresents who did what first. Falls back to occurredAt, then to
 * arrival order.
 */
export function toTimelineEntries(payload) {
  const entries = Array.isArray(payload)
    ? list(payload, normalizeTimelineEntry)
    : normalizeTimeline(payload).entries;

  const hasSequence = entries.every((e) => e.sequence !== null);
  if (hasSequence) return entries.slice().sort((a, b) => a.sequence - b.sequence);

  const hasDates = entries.every((e) => e.occurredAt !== null);
  if (hasDates) return entries.slice().sort((a, b) => Date.parse(a.occurredAt) - Date.parse(b.occurredAt));

  return entries; // partial ordering data — leave the server's order alone rather than shuffle it
}
