// Search State — the dedicated document search (NOT the Assistant). Holds the query, mode
// (structured|semantic), the live result, and the locally-persisted recent + saved searches.
//
// Sprint 4, Task 3 asked for a "Search Recent" cache. It already exists and is deliberately NOT
// migrated to services/cache.js, because the two store different KINDS of thing:
//
//   services/cache.js  caches SERVER RESPONSES in Paths.cache — data we can always refetch, which
//                      the OS is welcome to evict under storage pressure.
//   utils/storage.js   persists USER-AUTHORED data (api/search.js already keeps recent + saved here).
//
// A user's recent searches are not a copy of anything on the server; if they were evicted they would
// be gone for good. Moving them into an evictable cache would be a downgrade dressed as consistency.
// Search RESULTS are not cached either: a result is a ranked answer to a query at a moment in time,
// and replaying a stale one offline would present old rankings as current.
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { SearchService } from '../services';
import { isOffline } from '../api/_support';

const SearchContext = createContext(null);

export function SearchProvider({ children }) {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState('semantic'); // 'semantic' | 'structured'
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [offline, setOffline] = useState(false);
  const [recent, setRecent] = useState([]);
  const [saved, setSaved] = useState([]);
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  const loadLocal = useCallback(async () => {
    const [r, s] = await Promise.all([SearchService.getRecent(), SearchService.getSaved()]);
    if (!mounted.current) return;
    setRecent(r);
    setSaved(s);
  }, []);

  useEffect(() => { loadLocal(); }, [loadLocal]);

  const run = useCallback(async (overrideQuery) => {
    const q = (overrideQuery ?? query).trim();
    if (!q) return;
    if (overrideQuery !== undefined) setQuery(overrideQuery);
    setLoading(true);
    setError(null);
    try {
      const data = await SearchService.run({ query: q, mode });
      if (!mounted.current) return;
      setResult(data);
      setOffline(false);
      loadLocal(); // refresh recent list
    } catch (err) {
      if (!mounted.current) return;
      setOffline(isOffline(err));
      setError(err);
      setResult(null);
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, [query, mode, loadLocal]);

  const save = useCallback(async () => {
    if (!query.trim()) return;
    const next = await SearchService.save({ query: query.trim(), mode });
    if (mounted.current) setSaved(next);
  }, [query, mode]);

  const removeSaved = useCallback(async (id) => {
    const next = await SearchService.removeSaved(id);
    if (mounted.current) setSaved(next);
  }, []);

  const clearRecent = useCallback(async () => {
    await SearchService.clearRecent();
    if (mounted.current) setRecent([]);
  }, []);

  // Memoized so consumers get a stable value; the object literal rebuilt every render was
  // re-rendering the whole search screen on each keystroke (Sprint 4, Task 10).
  const value = useMemo(
    () => ({ query, setQuery, mode, setMode, result, loading, error, offline,
      recent, saved, run, save, removeSaved, clearRecent, reloadLocal: loadLocal }),
    [query, mode, result, loading, error, offline, recent, saved, run, save, removeSaved,
      clearRecent, loadLocal],
  );

  return <SearchContext.Provider value={value}>{children}</SearchContext.Provider>;
}

export function useSearch() {
  const ctx = useContext(SearchContext);
  if (!ctx) throw new Error('useSearch must be used within SearchProvider');
  return ctx;
}
