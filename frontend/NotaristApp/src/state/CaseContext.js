// Case State — the case list with search, status filter, and pagination. Shared between the Case List
// screen and anywhere that needs the current case set. Pagination uses refs (same pattern as the
// documents fix) so load-more reads the current page without a stale closure.
//
// Sprint 4 (Tasks 3+4): this context caches by hand rather than through useCachedResource, because
// the hook models ONE resource and this models a paginated, filtered query. What gets cached is
// deliberately narrow:
//
//   ONLY the unfiltered first page.
//
// Caching each (query, status, page) permutation would grow without bound and, worse, a restore
// could paint a filtered subset as though it were the whole list. Page 1 unfiltered is what the
// screen opens on, so it is the only page whose cache a user actually sees on a cold offline start.
//
// Note for anyone reading this while `caseEndpoint` is still false: CaseService serves labelled mock
// fixtures today, and mocks are never cached (see useCachedResource's note). So this cache is wired
// but inert until the real endpoint ships — by design, not by accident.
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { CaseService } from '../services';
import { isOffline } from '../api/_support';
import * as cache from '../services/cache';
import { CacheKeys } from '../services/cache';

const PAGE_SIZE = 20;
const CaseContext = createContext(null);

// The cache only ever represents this exact query: page 0, no text query, no status filter.
const isCacheableQuery = (page, q, st) => page === 0 && !q && !st;

export function CaseProvider({ children }) {
  const [cases, setCases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [offline, setOffline] = useState(false);
  const [usingMock, setUsingMock] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState(null);
  const [fromCache, setFromCache] = useState(false);
  const [lastSyncedAt, setLastSyncedAt] = useState(null);

  const nextPage = useRef(0);
  const inFlight = useRef(false);
  const generation = useRef(0);
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  const load = useCallback(async ({ reset = false, refresh = false, q = query, st = status } = {}) => {
    if (!reset && inFlight.current) return;
    if (reset) {
      generation.current += 1;
      if (refresh) setRefreshing(true); else setLoading(true);
    } else {
      inFlight.current = true;
      setLoadingMore(true);
    }
    const gen = generation.current;
    const page = reset ? 0 : nextPage.current;
    setError(null);
    try {
      const data = await CaseService.listCases({ page, size: PAGE_SIZE, query: q, status: st });
      if (!mounted.current || gen !== generation.current) return;
      const items = data.items ?? [];
      setCases((prev) => (reset ? items : [...prev, ...items]));
      const totalPages = data.page?.totalPages ?? 1;
      setHasMore(page < totalPages - 1);
      nextPage.current = page + 1;
      const mock = CaseService.usingMock;
      setUsingMock(mock);
      setOffline(false);
      setFromCache(false);

      if (!mock && isCacheableQuery(page, q, st)) {
        setLastSyncedAt(Date.now());
        // Not awaited — the list is already rendered; persisting is housekeeping.
        cache.write(CacheKeys.CASE_LIST, { items, hasMore: page < totalPages - 1 });
      }
    } catch (err) {
      if (!mounted.current) return;
      setOffline(isOffline(err));
      setError(err);
      // `cases` is left as-is: a failed refresh must not wipe a list the user is reading.
    } finally {
      if (!mounted.current) return;
      if (!reset) inFlight.current = false;
      setLoading(false);
      setRefreshing(false);
      setLoadingMore(false);
    }
  }, [query, status]);

  // Initial load only. Paints the cached first page first (if any), then refreshes behind it.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const hit = await cache.read(CacheKeys.CASE_LIST);
      if (cancelled || !mounted.current) return;
      // Guarded on emptiness: if the live load already won the race, its data is fresher and must
      // not be overwritten by the cache.
      if (hit && hit.data && Array.isArray(hit.data.items) && hit.data.items.length) {
        setCases((prev) => (prev.length ? prev : hit.data.items));
        setHasMore((prev) => prev || !!hit.data.hasMore);
        setFromCache(true);
        setLastSyncedAt(hit.storedAt);
        setLoading(false);
      }
    })();
    load({ reset: true });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Re-query when filters change (debounced by the caller via setQuery + applyFilters).
  const applyFilters = useCallback((next = {}) => {
    const q = next.query !== undefined ? next.query : query;
    const st = next.status !== undefined ? next.status : status;
    if (next.query !== undefined) setQuery(next.query);
    if (next.status !== undefined) setStatus(next.status);
    load({ reset: true, q, st });
  }, [load, query, status]);

  // Stable callbacks — these were inline arrows in the provider value, so every CaseProvider render
  // handed consumers new function identities and re-rendered the whole list (Sprint 4, Task 10).
  const refresh = useCallback(() => load({ reset: true, refresh: true }), [load]);
  const reload = useCallback(() => load({ reset: true }), [load]);
  const loadMore = useCallback(() => {
    if (hasMore && !inFlight.current) load({ reset: false });
  }, [hasMore, load]);

  const value = useMemo(
    () => ({
      cases, loading, refreshing, loadingMore, error, offline, usingMock, hasMore, query, status,
      fromCache, lastSyncedAt, setQuery, setStatus, applyFilters, refresh, loadMore, reload,
    }),
    [cases, loading, refreshing, loadingMore, error, offline, usingMock, hasMore, query, status,
      fromCache, lastSyncedAt, applyFilters, refresh, loadMore, reload],
  );

  return <CaseContext.Provider value={value}>{children}</CaseContext.Provider>;
}

export function useCases() {
  const ctx = useContext(CaseContext);
  if (!ctx) throw new Error('useCases must be used within CaseProvider');
  return ctx;
}
