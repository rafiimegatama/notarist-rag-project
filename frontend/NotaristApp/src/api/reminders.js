// reminderApi — SKMHT/APHT deadlines and workflow-queue reminders.
//
// Backed by GET /api/v1/reminders (ReminderController). The screens want ONE flat, sorted list and
// filter it client-side by window, so this module flattens the server's four proximity buckets back
// into a list. That is not fighting the server: bucketing and windowing use different thresholds
// (the server buckets OVERDUE/TODAY/7/30, the UI filters today/7d/30d cumulatively), and the flat
// list is what makes switching windows instant and offline-tolerant.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock, is404 } from './_support';
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
      const body = response.data?.data ?? {};

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
      // Endpoint not deployed in this environment: behave exactly as if the flag were still false.
      if (!is404(err)) throw err;
      return mock(MOCK_REMINDERS.map((r) => normalizeReminder(r, MOCK_NOW)), { label: 'reminders (404)' });
    }
  }
  // Use the fixtures' fixed "now" so mock severities stay stable/deterministic in review.
  return mock(MOCK_REMINDERS.map((r) => normalizeReminder(r, MOCK_NOW)), { label: 'reminders' });
}
