// Connectivity signal derived from real request outcomes (Sprint 4, Task 8).
//
// WHY NOT NetInfo: the app has no NetInfo/expo-network dependency, and adding one would answer a
// different question than the banner asks. NetInfo reports whether a radio is associated with a
// network — it says "connected" on a captive-portal wifi, on a VPN that drops packets, and while the
// backend is down. What the user needs to know is "is this app reaching its server right now", and
// the api layer already observes exactly that on every call. So connectivity is inferred from
// request outcomes rather than from the device's link state.
//
// The honest limit of this approach, stated plainly because the banner's credibility depends on it:
// this is a LAGGING signal. It cannot know the network dropped until a request tries and fails, and
// it cannot know the network returned until one succeeds. There is no background poll — a poll would
// burn battery and mislead by probing a route the user is not using. `RECONNECTING` is therefore
// shown only while a retry is genuinely in flight (retry.js drives it), never as a guess.
//
// This is a module singleton, matching `setAuthFailureHandler` in client.js — it is a transport-layer
// observable, not application state, and deliberately not a Context: no provider, no Redux, no new
// global store. Screens subscribe through the `useConnectivity` hook.

export const NetworkStatus = {
  ONLINE: 'online',
  OFFLINE: 'offline',
  RECONNECTING: 'reconnecting',
};

let status = NetworkStatus.ONLINE;
let lastSyncedAt = null;   // epoch ms of the last successful response
let lastError = null;      // ErrorKind of the failure that took us offline

const listeners = new Set();

function emit() {
  const snapshot = getNetworkState();
  // Copied before iterating: a listener may unsubscribe during notification.
  for (const listener of Array.from(listeners)) {
    try {
      listener(snapshot);
    } catch (_) {
      // One bad subscriber must not stop the others from hearing about the network.
    }
  }
}

export function getNetworkState() {
  return { status, lastSyncedAt, lastError };
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** A request succeeded: we are demonstrably reaching the backend. */
export function markOnline() {
  const changed = status !== NetworkStatus.ONLINE;
  status = NetworkStatus.ONLINE;
  lastSyncedAt = Date.now();
  lastError = null;
  // Emit on every success: `lastSyncedAt` changed even when the status did not, and the banner
  // renders it.
  emit();
  return changed;
}

/** A request failed for a network-class reason. HTTP errors do NOT come through here — a 500 proves
 *  the network is fine. */
export function markOffline(kind) {
  const changed = status !== NetworkStatus.OFFLINE || lastError !== kind;
  status = NetworkStatus.OFFLINE;
  lastError = kind || null;
  if (changed) emit();
  return changed;
}

/** A retry is in flight — we are actively trying to reach the server again. */
export function markReconnecting() {
  if (status === NetworkStatus.RECONNECTING) return false;
  status = NetworkStatus.RECONNECTING;
  emit();
  return true;
}

/** Test/dev seam: reset the singleton. Not called by app code. */
export function __reset() {
  status = NetworkStatus.ONLINE;
  lastSyncedAt = null;
  lastError = null;
  listeners.clear();
}
