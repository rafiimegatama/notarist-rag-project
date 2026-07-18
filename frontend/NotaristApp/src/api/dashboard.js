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
import { unwrap } from './envelope';
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

    // statistics is the only source of the per-state counters. Without it there is no dashboard —
    // and "no dashboard" is then the only honest thing to show.
    //
    // This used to answer a 404 with mock(computeDashboardSummary()), "treating its 404 the way the
    // flag being false is treated". On the LIVE path that let a routing change silently swap a
    // notary's real workload for counters computed from MOCK_CASES — a plausible dashboard with every
    // number invented. Sprint 7 verified GET /cases/statistics answers 200 against the running
    // backend, so a 404 here is a genuine fault; surface it like any other.
    if (statsRes.status === 'rejected') throw statsRes.reason;

    // unwrap() rather than `?.data?.data?.` chains. Same payload, but this is the module that
    // composes THREE endpoints — so it is the one most likely to meet a proxied or partial response,
    // and the one where a hand-rolled dig quietly yielding undefined turns into a dashboard of
    // zeroes rather than an error. A notary reading "0 menunggu verifikasi" cannot tell it apart
    // from a real empty queue.
    const statusCounts = unwrap(statsRes.value, null)?.statusCounts ?? {};
    const summary = summaryRes.status === 'fulfilled' ? unwrap(summaryRes.value, null) : null;
    const reminders = remindersRes.status === 'fulfilled' ? unwrap(remindersRes.value, null) : null;

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
    // Rethrow, always. This used to end `if (!is404(err)) throw err; return mock(...)`, which made
    // ANY 404 raised anywhere in the block above resolve to a dashboard computed from MOCK_CASES —
    // including the statistics 404 the block above now deliberately throws, so that fix would have
    // landed right back here and been converted into fixtures. On the live path a 404 is a fault
    // (route moved, gateway, revoked scope), and a notary's caseload is not something to invent when
    // we cannot read it. The try/catch is kept only for this comment and the rethrow's clarity.
    throw err;
  }
}
