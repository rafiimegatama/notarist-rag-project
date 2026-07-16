// Reminder normalization + urgency derivation. Severity is computed from the due date relative to
// "now" so the same reminder is styled consistently wherever it appears (dashboard, reminder list).
//
// Sprint 5 (Task 2): hardened against the real ReminderResponse.ReminderItem, which carries fields
// this never read — caseId, caseType, state, daysUntilDue. daysUntilDue in particular is the
// server's own overdue arithmetic; preferring it over a client-side date diff means the app and the
// backend cannot disagree about whether an SKMHT deadline has passed.
import { reminderTypeMeta } from '../constants/workflow';
import { pick, str, num, isoDate, withExtras, makeNormalizer } from './normalize';

export function deriveSeverity(dueDate, now = Date.now()) {
  if (!dueDate) return 'normal';
  const due = Date.parse(dueDate);
  if (Number.isNaN(due)) return 'normal';
  const diffDays = (due - now) / 86400000;
  if (diffDays < 0) return 'overdue';
  if (diffDays <= 3) return 'soon';
  return 'normal';
}

/**
 * Severity from the server's own day count, when it sent one.
 * `daysUntilDue` is documented as "whole days until due; negative when overdue".
 */
function severityFromDays(days) {
  if (days === null) return null;
  if (days < 0) return 'overdue';
  if (days <= 3) return 'soon';
  return 'normal';
}

const CONSUMED = [
  'id', 'type', 'reminderType', 'title', 'caseId', 'caseNumber', 'caseType', 'state',
  'dueDate', 'daysUntilDue', 'severity',
];

export function normalizeReminder(raw = {}, now = Date.now()) {
  // NEED_VERIFICATION was the old default. It is a claim about what kind of deadline this is, and
  // guessing it wrong mislabels a legal deadline — so an unknown type stays null and
  // reminderTypeMeta falls back to a neutral "Pengingat".
  const type = str(pick(raw, ['type']), null);
  const dueDate = isoDate(pick(raw, ['dueDate']), null);
  const daysUntilDue = num(pick(raw, ['daysUntilDue']), null);
  const rawCaseId = pick(raw, ['caseId']);

  const out = {
    id: str(pick(raw, ['id']), null),
    type,
    // Derived from the type, not sent by the server — the wire carries a vocabulary, not UI copy.
    title: str(pick(raw, ['title']), null) ?? reminderTypeMeta(type).label,
    caseId: rawCaseId === null ? null : String(rawCaseId),
    caseNumber: str(pick(raw, ['caseNumber']), null),
    caseType: str(pick(raw, ['caseType']), null),
    state: str(pick(raw, ['state']), null),
    dueDate,
    daysUntilDue,
    // Precedence: explicit severity > the server's day count > our own clock arithmetic. The middle
    // rung is new: it keeps "overdue" consistent with the backend even if the device clock is off,
    // which for an SKMHT deadline is the difference between a warning and a missed filing.
    severity: str(pick(raw, ['severity']), null)
      ?? severityFromDays(daysUntilDue)
      ?? deriveSeverity(dueDate, now),
  };
  return withExtras(out, raw, CONSUMED);
}

export const ReminderNormalizer = makeNormalizer((raw) => normalizeReminder(raw));

// Buckets a reminder into a filter window: 'today' | '7d' | '30d' (cumulative — a today item is
// also within 7d and 30d), used by the Reminder screen filter bar.
export function withinWindow(reminder, window, now = Date.now()) {
  if (!reminder.dueDate) return window === '30d';
  const diffDays = (Date.parse(reminder.dueDate) - now) / 86400000;
  if (window === 'today') return diffDays < 1;      // overdue + due today
  if (window === '7d') return diffDays < 7;
  return diffDays < 30;                              // '30d'
}
