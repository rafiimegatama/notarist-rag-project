// ReminderService — workflow reminders. Delegates to api/reminders (mock|http behind
// FEATURES.reminderEndpoint), which flattens the server's proximity buckets into the flat list the
// screens filter client-side.
import { FEATURES } from '../constants/config';
import { listReminders } from '../api/reminders';

export const ReminderService = {
  // The INTENDED path, not the path taken — see the same note in DashboardService. ReminderProvider
  // reads isMock(data) off the response instead, which also decides whether severity is derived
  // against MOCK_NOW or the real clock.
  usingMock: !FEATURES.reminderEndpoint,
  listReminders: () => listReminders(),
};
