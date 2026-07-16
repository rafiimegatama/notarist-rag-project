import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Generic async-state hook: runs `asyncFn` and exposes { data, loading, error, reload }. Handles
 * the loading→success/error lifecycle and ignores results from a stale run after unmount, so every
 * screen gets consistent loading/error/retry without re-implementing it.
 *
 * @param {Function} asyncFn  returns a promise
 * @param {Array} deps        re-run when these change
 * @param {boolean} immediate run on mount (default true)
 */
export default function useAsync(asyncFn, deps = [], immediate = true) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await asyncFn();
      if (mounted.current) setData(result);
      return result;
    } catch (err) {
      if (mounted.current) setError(err);
      return undefined;
    } finally {
      if (mounted.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (immediate) run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, loading, error, reload: run, setData };
}
