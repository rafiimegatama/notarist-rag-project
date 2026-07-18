// SyncContext — the queue's only subscriber that re-renders anything (Sprint 1).
//
// services/mutationQueue is a module singleton, like api/connectivity: transport machinery, not
// application state. This provider is the seam between the two — it subscribes once, holds the
// snapshot in React state, and owns the two triggers that decide WHEN the queue drains:
//
//   1. the network comes back   (api/connectivity, which already observes real request outcomes)
//   2. the app returns to the foreground (AppState)
//
// Both are here rather than inside the queue because both are lifecycle concerns, and a service that
// reaches for AppState is a service that cannot be tested without a device.
import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import * as queue from '../services/mutationQueue';
import { subscribe as subscribeNetwork, getNetworkState, NetworkStatus } from '../api/connectivity';

const SyncContext = createContext(null);

export function SyncProvider({ children }) {
  const [state, setState] = useState(() => queue.getQueueState());
  // Read in effects that must not re-subscribe every time the queue ticks.
  const stateRef = useRef(state);
  stateRef.current = state;

  // Restore the persisted queue on mount: this is what makes it survive a restart. Deliberately not
  // awaited by any screen — a queue with entries still to load must not delay first paint.
  useEffect(() => {
    let alive = true;
    queue.restore().then((s) => { if (alive) setState(s); });
    const unsubscribe = queue.subscribe((s) => { if (alive) setState(s); });
    return () => { alive = false; unsubscribe(); };
  }, []);

  // Trigger 1 — the network came back.
  //
  // Edge-triggered, on the OFFLINE/RECONNECTING -> ONLINE transition only. connectivity.markOnline()
  // emits on EVERY successful response (it updates lastSyncedAt), so a level-triggered "flush when
  // online" would kick the queue on every request the app makes.
  useEffect(() => {
    let previous = getNetworkState().status;
    return subscribeNetwork(({ status }) => {
      const cameBack = previous !== NetworkStatus.ONLINE && status === NetworkStatus.ONLINE;
      previous = status;
      if (cameBack && stateRef.current.pendingCount > 0) queue.flush();
    });
  }, []);

  // Trigger 2 — the app came back to the foreground.
  //
  // Covers the case the network signal cannot: connectivity is a LAGGING signal derived from request
  // outcomes (see its header), so an app backgrounded on a train and reopened on wifi has made no
  // request in between and still believes it is offline. Foregrounding is the moment a notary is
  // about to look at the data, and therefore the moment their pending writes should be on their way.
  useEffect(() => {
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active' && stateRef.current.pendingCount > 0) queue.flush();
    });
    return () => sub.remove();
  }, []);

  const value = useMemo(
    () => ({
      ...state,
      groups: queue.groupedByResource(),
      flush: queue.flush,
      cancel: queue.cancel,
      retry: queue.retry,
      QueueStatus: queue.QueueStatus,
      FailureReason: queue.FailureReason,
    }),
    [state],
  );

  return <SyncContext.Provider value={value}>{children}</SyncContext.Provider>;
}

/**
 * The queue, for any screen that shows or acts on pending writes.
 * -> { entries, groups, pendingCount, failedCount, flushing, lastFlushAt, flush, cancel, retry }
 *
 * Safe outside a SyncProvider: returns an inert, empty queue rather than throwing. A badge that
 * cannot find its provider should render nothing, not crash the screen it decorates.
 */
export function useSync() {
  return (
    useContext(SyncContext) ?? {
      entries: [],
      groups: [],
      pendingCount: 0,
      failedCount: 0,
      flushing: false,
      lastFlushAt: null,
      flush: async () => ({ sent: 0, failed: 0, remaining: 0 }),
      cancel: async () => false,
      retry: async () => false,
      QueueStatus: queue.QueueStatus,
      FailureReason: queue.FailureReason,
    }
  );
}

export default SyncContext;
