// DashboardService — aggregated workflow counters. Delegates to api/dashboard, which composes
// /cases/statistics + /dashboard/summary + /reminders (see that module for why it is three calls
// and not one).
import { FEATURES } from '../constants/config';
import { getDashboardSummary } from '../api/dashboard';

export const DashboardService = {
  // The INTENDED path, not the path taken. When the endpoint is enabled but answers 404 the api
  // layer falls back to fixtures, and this still reads false. Screens must decide "is this sample
  // data?" from the response via isMock(), which DashboardProvider does — never from this flag.
  // Kept because it answers a different, useful question: "is this build wired to a live backend?"
  usingMock: !FEATURES.dashboardEndpoint,
  getSummary: () => getDashboardSummary(),
};
