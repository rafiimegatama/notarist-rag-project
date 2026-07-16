// Central polling controller + staleness clock (Sprint 5, Tasks 5 + 6).
//
// One controller, not a setInterval per screen. The requirements it exists to satisfy:
//
//   * poll ONLY while a screen is visible; stop on blur                        (Task 6)
//   * refresh stale data on app resume and on screen focus                     (Task 5)
//   * NO duplicate requests                                                    (Task 5)
//
// The last one is why this is a module singleton rather than a hook's local state. The Dashboard
// reads the same reminder data the Reminder screen does; a per-screen timer means two screens, two
// timers, two requests for one answer. Registration is keyed by RESOURCE, so N subscribers to one
// resource produce one in-flight request and share its result.
//
// DESIGN NOTE — this deliberately does not fetch anything itself. It decides WHEN a resource is due
// and calls the refresher the owner registered. The contexts already own fetching, error handling
// and mock detection; duplicating any of that here would create a second, competing data path.

import { AppState } from 'react-native';
import { getNetworkState, NetworkStatus } from '../api/connectivity';

/** Per-resource poll cadence. Chosen from how fast the underlying data can actually change. */
export const POLL_INTERVALS = {
  // Workflow counters move when anyone in the office advances a case — minutes, not seconds.
  dashboard: 60000,
  // Deadlines change on a daily granularity; polling faster tells you nothing new.
  reminders: 120000,
  // A timeline appends when a pipeline stage completes; this is the liveliest of the four.
  timeline: 30000,
  // A conversation list changes only when this user talks to the assistant.
  conversations: 120000,
};

/** How old data must be before a focus/resume triggers a refresh. Below this, focusing is free. */
export const STALE_AFTER = {
  dashboard: 30000,
  reminders: 60000,
  timeline: 15000,
  conversations: 60000,
};

// resource -> { refresh, interval, staleAfter, subscribers:Set, timer, lastFetchedAt, inFlight }
const registry = new Map();

function entry(resource) {
  let e = registry.get(resource);
  if (!e) {
    e = {
      refresh: null,
      interval: POLL_INTERVALS[resource] ?? 60000,
      staleAfter: STALE_AFTER[resource] ?? 30000,
      subscribers: new Set(),
      timer: null,
      lastFetchedAt: 0,
      inFlight: null,
    };
    registry.set(resource, e);
  }
  return e;
}

/**
 * Register the function that refreshes a resource. Called once by the owning context.
 * Registering does NOT start polling — visibility does.
 */
export function registerResource(resource, refresh, options = {}) {
  const e = entry(resource);
  e.refresh = refresh;
  if (options.interval != null) e.interval = options.interval;
  if (options.staleAfter != null) e.staleAfter = options.staleAfter;
  return () => {
    // Only clear if we are still the registered refresher — a remount may have replaced us.
    if (e.refresh === refresh) {
      e.refresh = null;
      stopTimer(e);
    }
  };
}

/** True when the resource has not been fetched within its staleness window. */
export function isStale(resource, now = Date.now()) {
  const e = registry.get(resource);
  if (!e) return false;
  if (!e.lastFetchedAt) return true; // never fetched
  return now - e.lastFetchedAt >= e.staleAfter;
}

/**
 * Fetch a resource, coalescing concurrent callers (Task 5: "no duplicate requests").
 *
 * Every path into a refresh — the poll timer, a screen gaining focus, the app resuming — lands here.
 * They routinely coincide: bringing the app to the foreground fires resume AND focus, and may cross
 * a poll tick. Without coalescing that is three identical requests within a few milliseconds. The
 * in-flight promise is shared, so it is one.
 */
export function fetchResource(resource, { force = false } = {}) {
  const e = registry.get(resource);
  if (!e || !e.refresh) return Promise.resolve(undefined);

  if (e.inFlight) return e.inFlight;                       // coalesce
  if (!force && !isStale(resource)) return Promise.resolve(undefined); // still fresh — do nothing

  // Offline: a request cannot succeed, and firing one only produces a spurious error banner over
  // data the user can still read. The connectivity signal flips to online on the next successful
  // call, and the focus/resume path will pick it up then.
  if (getNetworkState().status === NetworkStatus.OFFLINE) return Promise.resolve(undefined);

  const p = Promise.resolve()
    .then(() => e.refresh())
    .then(
      (result) => { e.lastFetchedAt = Date.now(); return result; },
      (err) => {
        // Do NOT stamp lastFetchedAt on failure: the data is still stale, so the next focus should
        // try again rather than believe it just refreshed.
        throw err;
      },
    )
    .catch(() => undefined) // the context owns error state; polling must not raise unhandled rejections
    .finally(() => { e.inFlight = null; });

  e.inFlight = p;
  return p;
}

/** Record that a resource was fetched by someone else (its context's own load), so polling and
 *  focus do not immediately refetch what just arrived. */
export function markFetched(resource, at = Date.now()) {
  entry(resource).lastFetchedAt = at;
}

function startTimer(e, resource) {
  if (e.timer) return;
  e.timer = setInterval(() => { fetchResource(resource, { force: true }); }, e.interval);
}

function stopTimer(e) {
  if (!e.timer) return;
  clearInterval(e.timer);
  e.timer = null;
}

/**
 * A screen became visible. Refreshes if stale, and starts the poll timer.
 * Returns an unsubscribe that releases this subscriber and stops the timer when the last one leaves
 * — that IS the "stop on blur" requirement (Task 6).
 */
export function subscribeVisible(resource, subscriberId) {
  const e = entry(resource);
  e.subscribers.add(subscriberId);

  // Focus refresh (Task 5). Non-forced: a screen focused 2 seconds after its last fetch does not
  // refetch, which is the difference between "refresh on focus" and "hammer on every tab switch".
  fetchResource(resource);
  startTimer(e, resource);

  return () => {
    e.subscribers.delete(subscriberId);
    if (e.subscribers.size === 0) stopTimer(e); // nobody is looking — stop polling
  };
}

export function isPolling(resource) {
  const e = registry.get(resource);
  return !!(e && e.timer);
}

export function subscriberCount(resource) {
  const e = registry.get(resource);
  return e ? e.subscribers.size : 0;
}

// --- App resume (Task 5) ---------------------------------------------------------------------
//
// One AppState listener for the whole app. On background->active, every resource that someone is
// currently watching gets a staleness check. Resources nobody is watching are skipped: refreshing a
// screen that is not mounted is work with no observer.
let appStateSub = null;
let lastAppState = AppState.currentState;

export function startAppResumeWatch() {
  if (appStateSub) return () => {};
  appStateSub = AppState.addEventListener('change', (next) => {
    const resumed = (lastAppState === 'background' || lastAppState === 'inactive') && next === 'active';
    lastAppState = next;
    if (!resumed) return;
    for (const [resource, e] of registry.entries()) {
      if (e.subscribers.size > 0) fetchResource(resource); // stale-gated + coalesced
    }
  });
  return stopAppResumeWatch;
}

export function stopAppResumeWatch() {
  if (!appStateSub) return;
  appStateSub.remove();
  appStateSub = null;
}

/** Test/dev seam. Not called by app code. */
export function __reset() {
  for (const e of registry.values()) stopTimer(e);
  registry.clear();
  stopAppResumeWatch();
  lastAppState = 'active';
}
