// Dashboard State — the aggregated workflow counters shown on the dashboard cards. Isolated so the
// dashboard can refresh independently of the case list. Small on purpose: one summary object.
//
// Sprint 4: the load/refresh lifecycle moved into useCachedResource, which adds the cache-first read
// and background refresh (Tasks 3+4) that three contexts would otherwise each hand-roll. The
// behaviour this file used to own is unchanged apart from that — including the rule below.
import React, { createContext, useContext, useMemo } from 'react';
import { DashboardService } from '../services';
import useCachedResource from '../hooks/useCachedResource';
import { useResourceRegistration, useMarkFetched } from '../hooks/usePolledResource';
import { CacheKeys } from '../services/cache';
import { isMock } from '../api/_support';

const DashboardContext = createContext(null);

// Read the RESPONSE, not the feature flag. The flag says which path we intended to take; isMock says
// which one we actually came back from. They diverge whenever the endpoint is enabled but answers
// 404, and the api layer falls back to fixtures — and in exactly that case a flag-driven banner would
// stay hidden and show sample data as if it were real. useCachedResource takes this a step further
// and refuses to cache a mock at all; see the note in that hook.
const deriveMock = (data) => isMock(data);

export function DashboardProvider({ children }) {
  const {
    data: summary, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
    reload, refresh,
  } = useCachedResource({
    key: CacheKeys.DASHBOARD,
    fetcher: () => DashboardService.getSummary(),
    deriveMock,
  });

  // Sprint 5 (Tasks 5+6): tell the polling controller HOW to refresh this resource, and when it was
  // last loaded. Registration alone polls nothing — the DashboardScreen calling usePolledResource
  // ('dashboard') is what makes it live, and only while that screen is focused.
  useResourceRegistration('dashboard', refresh);
  useMarkFetched('dashboard', lastSyncedAt);

  // Memoized so the provider hands consumers a stable value: a fresh object literal here would
  // re-render every dashboard card on any parent render, defeating the memoization on StatCard
  // (Sprint 4, Task 10).
  const value = useMemo(
    () => ({ summary, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt, reload, refresh }),
    [summary, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt, reload, refresh],
  );

  return <DashboardContext.Provider value={value}>{children}</DashboardContext.Provider>;
}

export function useDashboard() {
  const ctx = useContext(DashboardContext);
  if (!ctx) throw new Error('useDashboard must be used within DashboardProvider');
  return ctx;
}
