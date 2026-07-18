// Bundle State — bundles for the currently open case, cached by caseId so re-opening a case is
// instant. Kept separate from Case State because bundles are a heavier, screen-scoped concern.
import React, { createContext, useCallback, useContext, useRef, useState, useEffect } from 'react';
import { BundleService } from '../services';
import { isOffline } from '../api/_support';

const BundleContext = createContext(null);

export function BundleProvider({ children }) {
  const [cache, setCache] = useState({});      // caseId -> bundles[]
  const [loadingCase, setLoadingCase] = useState(null);
  const [error, setError] = useState(null);
  const [offline, setOffline] = useState(false);
  const [usingMock, setUsingMock] = useState(false);
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  const loadForCase = useCallback(async (caseId, { force = false } = {}) => {
    if (!caseId) return;
    if (!force && cache[caseId]) return;       // served from cache
    setLoadingCase(caseId);
    setError(null);
    try {
      const data = await BundleService.listBundles(caseId);
      if (!mounted.current) return;
      setCache((prev) => ({ ...prev, [caseId]: data }));
      setUsingMock(BundleService.usingMock);
      setOffline(false);
    } catch (err) {
      if (!mounted.current) return;
      setOffline(isOffline(err));
      setError(err);
    } finally {
      // Only clear our own flag. Opening case B while case A's load is still in flight sets
      // loadingCase to B; when A's request settles, an unconditional null here wiped B's flag and
      // its skeleton while B was still loading.
      if (mounted.current) setLoadingCase((current) => (current === caseId ? null : current));
    }
  }, [cache]);

  const bundlesFor = useCallback((caseId) => cache[caseId] ?? null, [cache]);

  return (
    <BundleContext.Provider
      value={{ bundlesFor, loadForCase, loadingCase, error, offline, usingMock,
        isLoading: (caseId) => loadingCase === caseId }}
    >
      {children}
    </BundleContext.Provider>
  );
}

export function useBundles() {
  const ctx = useContext(BundleContext);
  if (!ctx) throw new Error('useBundles must be used within BundleProvider');
  return ctx;
}
