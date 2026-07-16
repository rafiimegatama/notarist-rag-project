// dashboardApi — the workflow counters on the dashboard header.
//
// ─────────────────────────────────────────────────────────────────────────────
// WHY THIS COMPOSES THREE ENDPOINTS INSTEAD OF CALLING /dashboard/summary
//
// GET /api/v1/dashboard/summary looks like the obvious source and is the wrong one. Its counts are
// coarse ROLL-UPS: CaseAnalyticsService.bucketOf() folds WAITING_NOTARY_APPROVAL and FINALIZED into
// a single `approved` bucket, and WAITING_VERIFICATION together with VERIFIED into `verification`.
// The dashboard shows "Menunggu Approval" and "Siap Kirim" as two separate cards, so a summary that
// has already added them together cannot answer it. Using summary.approved for either one would
// double-count the other — a plausible-looking number that is simply wrong.
//
// GET /api/v1/cases/statistics returns statusCounts keyed by the EXACT CaseState, which maps 1:1
// onto what each card asks for. So:
//
//   /cases/statistics  -> every per-state counter (the real source)
//   /dashboard/summary -> totalCases (tenant-wide total, authoritative)
//   /reminders         -> reminderCount + overdueSkmht (not case state at all)
//
// Three calls, in parallel. Every number traceable to one server field.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock, is404 } from './_support';
import { computeDashboardSummary } from '../mocks/fixtures';

// CaseState -> the counter each dashboard card reads. The UI's status vocabulary is older and
// smaller than the domain's 16-state lifecycle, so this is a projection, not a rename:
//
//   draft         <- GENERATING_DRAFT only. DRAFT_FAILED is a failure needing attention, not a
//                    draft in progress; counting it here would hide failures inside a normal number.
//   readyToSend   <- FINALIZED. Finalised but not yet DELIVERED is exactly "siap kirim".
//   waitingApproval <- WAITING_NOTARY_APPROVAL.
//
// The other 11 states (CASE_CREATED, UPLOADING, OCR_RUNNING, OCR_FAILED, FIELD_EXTRACTION, VERIFIED,
// DRAFT_FAILED, QC_FAILED, QC_APPROVED, DELIVERED, ARCHIVED) have no card. They are not lost — they
// are counted in totalCase — they just have nowhere to show. That is a UI gap, not a data gap.
const STATE_TO_COUNTER = {
  draft: ['GENERATING_DRAFT'],
  waitingVerification: ['WAITING_VERIFICATION'],
  waitingQc: ['WAITING_QC'],
  waitingApproval: ['WAITING_NOTARY_APPROVAL'],
  readyToSend: ['FINALIZED'],
};

const sumStates = (statusCounts, states) =>
  states.reduce((acc, s) => acc + Number(statusCounts?.[s] ?? 0), 0);

// GET /dashboard/summary + /cases/statistics + /reminders -> the UI counter contract:
// { totalCase, draft, waitingVerification, waitingQc, waitingApproval, readyToSend, overdueSkmht,
//   reminderCount }
export async function getDashboardSummary() {
  if (!FEATURES.dashboardEndpoint) {
    return mock(computeDashboardSummary(), { label: 'dashboard' });
  }

  try {
    // allSettled, not all: reminders failing must not blank the case counters, and vice versa. A
    // partially-degraded dashboard beats an all-or-nothing one, as long as the gap is visible.
    const [statsRes, summaryRes, remindersRes] = await Promise.allSettled([
      client.get('/cases/statistics'),
      client.get('/dashboard/summary'),
      client.get('/reminders'),
    ]);

    // statistics is the only source of the per-state counters. Without it there is no dashboard, so
    // treat its 404 the way the flag being false is treated.
    if (statsRes.status === 'rejected') {
      if (!is404(statsRes.reason)) throw statsRes.reason;
      return mock(computeDashboardSummary(), { label: 'dashboard (statistics 404)' });
    }

    const statusCounts = statsRes.value?.data?.data?.statusCounts ?? {};
    const summary = summaryRes.status === 'fulfilled' ? summaryRes.value?.data?.data : null;
    const reminders = remindersRes.status === 'fulfilled' ? remindersRes.value?.data?.data : null;

    // A non-404 failure on a secondary source is a real backend problem. Surface it rather than
    // quietly showing a dashboard with holes in it.
    if (summaryRes.status === 'rejected' && !is404(summaryRes.reason)) throw summaryRes.reason;
    if (remindersRes.status === 'rejected' && !is404(remindersRes.reason)) throw remindersRes.reason;

    const counters = Object.fromEntries(
      Object.entries(STATE_TO_COUNTER).map(([counter, states]) => [counter, sumStates(statusCounts, states)]),
    );

    // Prefer the summary's total (tenant-wide, computed server-side). Fall back to summing the
    // per-state counts, which is the same population by a different route.
    const totalCase = summary
      ? Number(summary.totalCases ?? 0)
      : Object.values(statusCounts).reduce((a, b) => a + Number(b ?? 0), 0);

    // null, not 0, when reminders are unavailable. 0 is a claim ("nothing is overdue"); null is the
    // truth ("we do not know"). The brief's "no placeholder counters" rules out the comfortable lie.
    const overdueSkmht = reminders
      ? (reminders.overdue ?? []).filter((r) => r.reminderType === 'SKMHT_DEADLINE').length
      : null;
    const reminderCount = reminders ? Number(reminders.totalCount ?? 0) : null;

    return { totalCase, ...counters, overdueSkmht, reminderCount, __mock: false };
  } catch (err) {
    if (!is404(err)) throw err;
    return mock(computeDashboardSummary(), { label: 'dashboard (404)' });
  }
}
