// Cache-first resource loading with background refresh (Sprint 4, Tasks 3 + 4).
//
// The lifecycle this encodes, which is the same one three contexts were about to hand-roll:
//
//   1. peek memory synchronously — a warm cache paints on the FIRST render, no skeleton flash
//   2. read disk — a cold-but-cached start paints as soon as the file is parsed
//   3. fetch anyway, always, in the background — cache is shown, never trusted as final
//   4. on success: swap in fresh data, write it back to the cache
//   5. on failure WITH cache: keep showing the cache and surface the error alongside it, do not blank
//      a working screen because a refresh failed
//   6. on failure WITHOUT cache: normal error state
//
// Step 5 is the point of the whole exercise. Before this, a failed refresh replaced a perfectly good
// case list with an error panel; a user on a train lost the data they already had.
//
// ---------------------------------------------------------------------------------------------
// MOCK RESPONSES ARE NEVER CACHED.
//
// Services fall back to labelled fixtures while their endpoints are missing, and screens render a
// "data contoh" banner by reading `__mock` off the RESPONSE (see DashboardContext's note on why the
// response and not the flag). Caching a fixture would break that honesty in the worst way: the entry
// would outlive the session, and a later run — possibly after the real endpoint shipped — would
// paint fixture numbers with no banner, indistinguishable from real data. Sample data must never
// survive the process that labelled it. So `write` is skipped whenever isMock(data).
// ---------------------------------------------------------------------------------------------

import { useCallback, useEffect, useRef, useState } from 'react';
import * as cache from '../services/cache';
import { isMock, isOffline } from '../api/_support';

/**
 * @param {Object}   opts
 * @param {string}   opts.key       CacheKeys entry
 * @param {Function} opts.fetcher   () => Promise<data>
 * @param {Function} [opts.deriveMock]  (data) => boolean. Defaults to reading `__mock` off the
 *                                      response; pass one for services that report mock separately.
 * @param {boolean}  [opts.enabled=true]
 */
export default function useCachedResource({ key, fetcher, deriveMock, enabled = true }) {
  // Synchronous memory peek in the initial state: if this session already loaded the resource, the
  // very first render has data. useState's initializer runs once, so this costs one Map lookup.
  const initial = enabled ? cache.peek(key) : null;

  const [data, setData] = useState(initial ? initial.data : null);
  const [loading, setLoading] = useState(enabled && !initial);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [offline, setOffline] = useState(false);
  const [usingMock, setUsingMock] = useState(false);
  const [fromCache, setFromCache] = useState(!!initial);
  const [lastSyncedAt, setLastSyncedAt] = useState(initial ? initial.storedAt : null);

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  // Read through refs so `load` stays referentially stable: it lands in a context value and in a
  // mount effect, and a new identity every render would re-fire both.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const deriveMockRef = useRef(deriveMock);
  deriveMockRef.current = deriveMock;

  // Tracks whether data is currently on screen, without making `load` depend on `data` (which would
  // rebuild it on every fetch).
  const hasDataRef = useRef(!!initial);

  const load = useCallback(async ({ refresh = false } = {}) => {
    if (!enabled) return;

    // A background refresh must not blank the screen: with data already up, this is `refreshing`
    // (which drives the pull-to-refresh spinner), never `loading` (which drives the skeleton).
    if (refresh || hasDataRef.current) setRefreshing(true);
    else setLoading(true);
    setError(null);

    try {
      const fresh = await fetcherRef.current();
      if (!mounted.current) return;

      const mock = deriveMockRef.current ? deriveMockRef.current(fresh) : isMock(fresh);
      setData(fresh);
      setUsingMock(mock);
      setOffline(false);
      setFromCache(false);
      hasDataRef.current = fresh != null;

      if (!mock && fresh != null) {
        const storedAt = Date.now();
        setLastSyncedAt(storedAt);
        // Deliberately not awaited: persisting is housekeeping, and the screen has the data already.
        cache.write(key, fresh);
      }
    } catch (err) {
      if (!mounted.current) return;
      setOffline(isOffline(err));
      setError(err);
      // `data` is intentionally left alone. If a cache is on screen it stays on screen; the caller
      // decides how to present `error` next to it (usually an inline banner, not a full error page).
    } finally {
      if (!mounted.current) return;
      setLoading(false);
      setRefreshing(false);
    }
  }, [key, enabled]);

  // Mount: fill from disk if memory missed, then always refresh in the background.
  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;

    (async () => {
      if (!hasDataRef.current) {
        const hit = await cache.read(key);
        if (cancelled || !mounted.current) return;
        if (hit && hit.data != null) {
          setData(hit.data);
          setFromCache(true);
          setLastSyncedAt(hit.storedAt);
          setLoading(false);
          hasDataRef.current = true;
        }
      }
      if (cancelled || !mounted.current) return;
      load();
    })();

      return () => { cancelled = true; };
  }, [key, enabled, load]);

  // Stable identity: `refresh` is handed to contexts that memoize their provider value, and an inline
  // arrow here would change every render and invalidate every one of those memos.
  const refresh = useCallback(() => load({ refresh: true }), [load]);

  return {
    data,
    setData,
    loading,
    refreshing,
    error,
    offline,
    usingMock,
    /** True while the data on screen came from the cache and no fresh response has landed yet. */
    fromCache,
    /** When the on-screen data was fetched. Null until a real (non-mock) response has been stored. */
    lastSyncedAt,
    reload: load,
    refresh,
  };
}
