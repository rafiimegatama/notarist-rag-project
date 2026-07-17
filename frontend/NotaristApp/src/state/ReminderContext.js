// Reminder State — all reminders held in memory, with a time-window filter (today / 7d / 30d) applied
// client-side so switching windows is instant. Shared between the dashboard reminder card and the
// Reminder screen.
//
// Sprint 4: load/refresh moved to useCachedResource (cache-first + background refresh, Tasks 3+4).
// The window filter stays here — it is view state, not fetched data, and never touches the cache.
import React, { createContext, useContext, useMemo, useState } from 'react';
import { ReminderService } from '../services';
import { withinWindow } from '../models/Reminder';
import { isMock } from '../api/_support';
import { MOCK_NOW } from '../mocks/fixtures';
import useCachedResource from '../hooks/useCachedResource';
import { useResourceRegistration, useMarkFetched } from '../hooks/usePolledResource';
import { CacheKeys } from '../services/cache';

const ReminderContext = createContext(null);

// Read the RESPONSE, not the feature flag — the two diverge when the endpoint is enabled but answers
// 404 and the api layer falls back to fixtures. Here that divergence has teeth beyond the banner:
// `now` below is chosen from usingMock, so a flag-driven value would filter real reminders against
// the fixtures' frozen clock and quietly show the wrong window.
const deriveMock = (data) => isMock(data);

// Stable identity for the empty case: `data || []` would hand `filtered` a new array every render.
const EMPTY = [];

export function ReminderProvider({ children }) {
  const [window, setWindow] = useState('7d'); // 'today' | '7d' | '30d'

  const {
    data, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt, reload, refresh,
  } = useCachedResource({
    key: CacheKeys.REMINDERS,
    fetcher: () => ReminderService.listReminders(),
    deriveMock,
  });

  // Sprint 5 (Tasks 5+6). Registered here, polled only while a screen watching 'reminders' is
  // focused — which includes the Dashboard, since its reminder counters read the same resource.
  // Two screens, one timer, one request (see services/polling.js).
  useResourceRegistration('reminders', refresh);
  useMarkFetched('reminders', lastSyncedAt);

  // The hook starts at null; every consumer here expects a list.
  const reminders = data || EMPTY;

  // When serving mock data, filter against the fixtures' fixed "now" so windows stay deterministic.
  //
  // Quantized to the minute (Sprint 4, Task 10): a raw Date.now() is a new value on every render, so
  // it invalidated the `filtered` memo below every single time and the filter ran on each render for
  // no benefit. The windows are day-granular ('today' / '7d' / '30d'), so a minute of skew cannot
  // change which bucket a reminder falls in — this is a free memo, not a trade.
  const now = usingMock ? MOCK_NOW : Math.floor(Date.now() / 60000) * 60000;
  const filtered = useMemo(
    () => reminders.filter((r) => withinWindow(r, window, now)),
    [reminders, window, now],
  );

  const value = useMemo(
    () => ({
      reminders, filtered, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
      window, setWindow, reload, refresh, count: reminders.length,
    }),
    [reminders, filtered, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
      window, reload, refresh],
  );

  return <ReminderContext.Provider value={value}>{children}</ReminderContext.Provider>;
}

export function useReminders() {
  const ctx = useContext(ReminderContext);
  if (!ctx) throw new Error('useReminders must be used within ReminderProvider');
  return ctx;
}
