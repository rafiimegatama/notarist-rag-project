// reminderApi — SKMHT/APHT deadlines and workflow-queue reminders.
//
// Backed by GET /api/v1/reminders (ReminderController). The screens want ONE flat, sorted list and
// filter it client-side by window, so this module flattens the server's four proximity buckets back
// into a list. That is not fighting the server: bucketing and windowing use different thresholds
// (the server buckets OVERDUE/TODAY/7/30, the UI filters today/7d/30d cumulatively), and the flat
// list is what makes switching windows instant and offline-tolerant.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock } from './_support';
import { unwrap } from './envelope';
import { normalizeReminder } from '../models/Reminder';
import { MOCK_REMINDERS, MOCK_NOW } from '../mocks/fixtures';

// The backend's ReminderItem.reminderType vocabulary -> the UI's REMINDER_TYPE keys
// (constants/workflow.js). A rename only: every target already exists with a label and an icon.
//
// Note what is NOT here: the UI knows EXPIRED_NPWP and EXPIRED_NIB, and nothing on the backend
// produces them — no rule watches identity-document expiry. Those reminders simply never arrive.
// They are left in REMINDER_TYPE rather than deleted, because the gap is in the backend, not in the
// UI, and reminderTypeMeta() falls back safely for any type it does not recognise.
const TYPE_MAP = {
  SKMHT_DEADLINE: 'SKMHT',
  APHT_DEADLINE: 'APHT',
  PENDING_VERIFICATION: 'NEED_VERIFICATION',
  PENDING_QC: 'NEED_QC',
  PENDING_APPROVAL: 'NEED_APPROVAL',
};

// ReminderResponse returns items grouped, not identified: there is no reminder ID, because a
// reminder is derived from a case's state rather than stored. One case can raise at most one
// reminder per type, so caseId + reminderType is a stable key for React lists.
function toReminder(item) {
  const type = TYPE_MAP[item.reminderType] ?? item.reminderType;
  return normalizeReminder({
    id: `${item.caseId}:${item.reminderType}`,
    type,
    // title is intentionally omitted: normalizeReminder derives it from reminderTypeMeta(type),
    // which is where every other localized workflow label already comes from. The server sends a
    // type, not display copy — inventing a title here would be putting Indonesian UI strings in the
    // API layer.
    caseNumber: item.caseNumber,
    dueDate: item.dueDate,
  });
}

// GET /api/v1/reminders -> { generatedAt, totalCount, countsByBucket, overdue[], today[],
//                            within7Days[], within30Days[] }
export async function listReminders() {
  if (FEATURES.reminderEndpoint) {
    try {
      const response = await client.get('/reminders');
      // unwrap(), not `response.data?.data ?? {}`: the same payload, but it also tolerates a body
      // that is not the envelope, so a gateway HTML page yields an empty reminder list instead of an
      // exception thrown from a property read on a string.
      const body = unwrap(response, null) ?? {};

      // Order matters: soonest-due first overall. The server sorts within each bucket, and the
      // buckets themselves are already in urgency order.
      const flat = [
        ...(body.overdue ?? []),
        ...(body.today ?? []),
        ...(body.within7Days ?? []),
        ...(body.within30Days ?? []),
      ].map(toReminder);

      // Severity is derived against the real clock, not MOCK_NOW — normalizeReminder defaults `now`
      // to Date.now(), and ReminderContext switches away from MOCK_NOW as soon as usingMock is false.
      return Object.assign(flat, { __mock: false });
    } catch (err) {
      // A 404 is an ERROR here, not a cue to invent reminders. This used to read "endpoint not
      // deployed in this environment: behave exactly as if the flag were still false" and answered a
      // 404 with MOCK_REMINDERS — on the LIVE path, with the flag ON.
      //
      // Sprint 7 verified GET /api/v1/reminders against the running backend: it exists and answers
      // 200. The premise is gone, and what the fallback actually did was fabricate SKMHT and APHT
      // deadlines — statutory dates, computed against MOCK_NOW, a clock that is not this one — and
      // hand them to a notary as their queue. "The route moved" and "your SKMHT is overdue" are not
      // interchangeable claims, and a 404 can only ever mean the first.
      //
      // Reminders have no honest mock for the reason search and the assistant have none: each one is
      // a legal deadline on a real case, not a placeholder. Let it throw — ReminderContext already
      // classifies an ApiError and the screen renders ErrorState.
      throw err;
    }
  }
  // Use the fixtures' fixed "now" so mock severities stay stable/deterministic in review.
  return mock(MOCK_REMINDERS.map((r) => normalizeReminder(r, MOCK_NOW)), { label: 'reminders' });
}
