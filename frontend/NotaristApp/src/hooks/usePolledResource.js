// Screen-facing side of the polling controller (Sprint 5, Tasks 5 + 6).
//
// Two hooks, deliberately split:
//
//   useResourceRegistration(resource, refresh)  — the OWNER (a context) says how to refresh.
//   usePolledResource(resource)                 — a SCREEN says "I am visible; keep this fresh".
//
// The split matters because ownership and visibility are different facts. The context owns fetching
// whether or not anything is on screen; the screen knows whether anyone is looking. Collapsing them
// would either poll for unmounted screens or lose the refresher when a screen blurs.
//
// WHY useIsFocused AND NOT useFocusEffect: this needs to know "am I focused" as a value, to key an
// effect that also depends on the resource. useFocusEffect wants a stable callback and re-runs on
// every focus regardless — with the same subscribe/unsubscribe pair, the value form is simpler to
// reason about and unsubscribes identically on blur and on unmount.

import { useEffect } from 'react';
import { useIsFocused } from '@react-navigation/native';
import { registerResource, subscribeVisible, markFetched } from '../services/polling';

/**
 * Register how a resource refreshes. Call from the context that owns it.
 * `refresh` must return a promise; polling awaits it to coalesce concurrent callers.
 */
export function useResourceRegistration(resource, refresh, options) {
  useEffect(() => {
    if (!refresh) return undefined;
    return registerResource(resource, refresh, options);
    // `options` is intentionally not a dependency: it is a literal at every call site, and including
    // it would re-register on every render for no behavioural gain.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resource, refresh]);
}

/**
 * Keep `resource` fresh while this screen is focused, and stop the moment it is not.
 *
 * On focus: refreshes if stale, then polls at the resource's cadence.
 * On blur/unmount: unsubscribes. When the last viewer leaves, the controller stops the timer — so a
 * backgrounded tab costs nothing.
 */
export default function usePolledResource(resource, { enabled = true } = {}) {
  const isFocused = useIsFocused();

  useEffect(() => {
    if (!enabled || !isFocused) return undefined;
    // A stable-per-screen id would be nicer, but a fresh object identity per focus is exactly the
    // right key here: it is unique per (screen, focus) and released on blur.
    const subscriberId = {};
    return subscribeVisible(resource, subscriberId);
  }, [resource, isFocused, enabled]);
}

/** Tell the controller a resource was just loaded by its context, so focus/polling do not refetch
 *  data that arrived milliseconds ago. */
export function useMarkFetched(resource, lastSyncedAt) {
  useEffect(() => {
    if (lastSyncedAt) markFetched(resource, lastSyncedAt);
  }, [resource, lastSyncedAt]);
}
