// Global loading (Sprint 9) — the app-level busy state for actions that block the WHOLE app, not a
// single screen. Screen-local waits keep using LoadingState/skeletons; this is for the handful of
// things a notary must not interact around while they finish (signing out, an app-wide sync).
//
// Shape of the feature, matched to the sprint:
//   * Loading Context   — this provider; begin/update/end/cancel + a withLoading() wrapper.
//   * Queue             — many tasks can run at once; they are held in order, and the overlay shows
//                         the most recent plus a count. One task ending does not dismiss another's.
//   * Progress          — a task may carry a determinate 0..1 progress, updated as work advances.
//   * Cancelable        — a task may expose onCancel; the overlay renders a Batalkan button that runs
//                         it. withLoading wires an AbortController so the work can observe the signal.
//
// Two contexts on purpose. The ACTION api (begin/end/…) is stable for the life of the provider, so a
// component that only triggers loading never re-renders when the task list changes. The TASK list is
// its own context, read only by the overlay. Collapsing them would re-render every begin() caller on
// every progress tick — the same re-render trap StatCard and the memoized rows guard against.
import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';

const LoadingApiContext = createContext(null);
const LoadingStateContext = createContext(null);

const clamp01 = (n) => Math.max(0, Math.min(1, n));
const EMPTY = [];

export function LoadingProvider({ children }) {
  const [tasks, setTasks] = useState(EMPTY);
  // Cancel handlers live in a ref, not in task state: they are functions (not renderable data) and
  // may close over the latest work, so they must not be frozen into a state snapshot.
  const cancelHandlers = useRef(new Map());
  const idRef = useRef(0);

  const begin = useCallback((opts = {}) => {
    const id = `load-${(idRef.current += 1)}`;
    const task = {
      id,
      message: opts.message != null ? opts.message : 'Memuat…',
      progress: typeof opts.progress === 'number' ? clamp01(opts.progress) : null,
      cancelable: !!opts.cancelable || typeof opts.onCancel === 'function',
      blocking: opts.blocking !== false, // blocking by default; pass blocking:false for background work
      startedAt: Date.now(),
    };
    if (typeof opts.onCancel === 'function') cancelHandlers.current.set(id, opts.onCancel);
    setTasks((prev) => [...prev, task]);
    return id;
  }, []);

  const update = useCallback((id, patch = {}) => {
    setTasks((prev) => {
      let changed = false;
      const next = prev.map((t) => {
        if (t.id !== id) return t;
        changed = true;
        const merged = { ...t };
        if ('message' in patch && patch.message != null) merged.message = patch.message;
        if ('progress' in patch) merged.progress = typeof patch.progress === 'number' ? clamp01(patch.progress) : null;
        return merged;
      });
      return changed ? next : prev; // no identity churn for an update to a task that already ended
    });
  }, []);

  const end = useCallback((id) => {
    cancelHandlers.current.delete(id);
    setTasks((prev) => (prev.some((t) => t.id === id) ? prev.filter((t) => t.id !== id) : prev));
  }, []);

  const cancel = useCallback((id) => {
    const handler = cancelHandlers.current.get(id);
    // Run the caller's cancel FIRST (abort the in-flight work), then drop the task. If the work is
    // wrapped by withLoading, its finally still calls end(id) — end() is idempotent, so a double
    // removal is a no-op rather than a bug.
    try { handler?.(); } catch (_) { /* a throwing cancel handler must not wedge the overlay open */ }
    end(id);
  }, [end]);

  /**
   * Run async work under a global loading task, cleaning up even if it throws.
   *
   * @param {Function|Promise} work  a promise, or a function receiving { update, signal, id }.
   *                                 `update({progress,message})` advances the task; `signal` is an
   *                                 AbortSignal when opts.cancelable is set.
   * @param {Object} opts            { message, progress, blocking, cancelable, onCancel } — see begin.
   */
  const withLoading = useCallback(async (work, opts = {}) => {
    // AbortController is present on RN (Hermes) and react-native-web; guard anyway so an exotic
    // runtime without it degrades to a non-abortable signal rather than throwing before work runs.
    const controller = opts.cancelable && typeof AbortController !== 'undefined' ? new AbortController() : null;
    const id = begin({
      ...opts,
      cancelable: opts.cancelable || typeof opts.onCancel === 'function',
      onCancel: () => { controller?.abort(); opts.onCancel?.(); },
    });
    try {
      return typeof work === 'function'
        ? await work({ update: (patch) => update(id, patch), signal: controller ? controller.signal : undefined, id })
        : await work;
    } finally {
      end(id);
    }
  }, [begin, update, end]);

  const api = useMemo(() => ({ begin, update, end, cancel, withLoading }), [begin, update, end, cancel, withLoading]);

  return (
    <LoadingApiContext.Provider value={api}>
      <LoadingStateContext.Provider value={tasks}>
        {children}
      </LoadingStateContext.Provider>
    </LoadingApiContext.Provider>
  );
}

// Safe outside a provider: returns a no-op api whose withLoading STILL RUNS THE WORK. A missing
// provider must never mean a signOut silently does nothing — it means it runs without the overlay.
const FALLBACK_API = {
  begin: () => 'noop',
  update: () => {},
  end: () => {},
  cancel: () => {},
  withLoading: async (work) =>
    (typeof work === 'function' ? work({ update: () => {}, signal: undefined, id: 'noop' }) : work),
};

/** The action api: begin/update/end/cancel/withLoading. Stable identity; safe to depend on. */
export function useGlobalLoading() {
  return useContext(LoadingApiContext) || FALLBACK_API;
}

/** The live task list — read by the overlay. Empty array outside a provider. */
export function useLoadingTasks() {
  return useContext(LoadingStateContext) || EMPTY;
}
