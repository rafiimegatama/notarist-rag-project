// Local conversation overrides (Sprint 3) — pin + rename for the Conversation History screen.
//
// There is no backend for this. AssistantController serves per-session history and nothing else — no
// "list conversations", no rename, no pin (FEATURES.conversationListEndpoint is false, and the list
// itself is marked mock). But pin and rename are LOCAL organisational preferences, not server facts:
// which conversations a notary keeps at the top, and what they call them, is the same kind of state
// as theme or language. So it is stored the same way — on the device, per user — and OVERLAID on the
// conversation list (mock today, the real endpoint the day it ships) rather than sent anywhere.
//
// This mirrors services/mutationQueue's shape deliberately: a module singleton with a subscribe seam,
// scoped per user. It is scoped for the same reason the queue and cache are — a notary office device
// is shared, and one notary's private label for a conversation ("Sengketa waris Budi") must not
// surface under another's login. Scoping, not sign-out clearing, is what isolates them: pins survive
// a notary signing back in (like the queue), but are unreachable under anyone else's scope.
//
// Values are NON-SENSITIVE-preference-grade and go through utils/storage (SecureStore, in-memory
// fallback on web) — the same store theme and language use. A rename that fails to persist is not
// worth failing the user's tap over, so every write is best-effort and the in-memory copy always wins
// for the session.

import * as storage from '../utils/storage';

const KEY_PREFIX = 'conv_overrides_v1';

let scope = 'anon';
let overrides = {};          // { [sessionId]: { pinned?: boolean, title?: string } }
let loadedScope = null;      // scope whose disk copy is already in `overrides`

const listeners = new Set();

const storageKey = () => `${KEY_PREFIX}::${scope}`;

function emit() {
  const snapshot = getSnapshot();
  for (const listener of Array.from(listeners)) {
    try { listener(snapshot); } catch (_) { /* one bad subscriber must not silence the others */ }
  }
}

async function persist() {
  try {
    await storage.setItem(storageKey(), JSON.stringify(overrides));
  } catch (_) {
    /* best-effort — the in-memory copy carries this session */
  }
}

/** A frozen copy, safe to hold in React state. */
export function getSnapshot() {
  return { ...overrides };
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Scope overrides to a user, mirroring cache.setCacheScope / queue.setQueueScope. */
export async function setOverridesScope(userId) {
  const next = userId ? String(userId) : 'anon';
  if (next === scope) return;
  scope = next;
  overrides = {};
  loadedScope = null;
  await restore();
}

/** Load the current scope's overrides from disk. Idempotent per scope. */
export async function restore() {
  if (loadedScope === scope) return getSnapshot();
  loadedScope = scope;
  try {
    const raw = await storage.getItem(storageKey());
    const parsed = raw ? JSON.parse(raw) : null;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) overrides = parsed;
  } catch (_) {
    // Corrupt/absent — start empty rather than throwing inside a preference load.
  }
  emit();
  return getSnapshot();
}

function mutate(sessionId, patch) {
  if (!sessionId) return;
  const current = overrides[sessionId] || {};
  const nextEntry = { ...current, ...patch };
  // Drop keys that returned to their default so the map does not grow entries that mean "no override".
  if (nextEntry.pinned === false) delete nextEntry.pinned;
  if (nextEntry.title == null || nextEntry.title === '') delete nextEntry.title;
  if (Object.keys(nextEntry).length === 0) {
    delete overrides[sessionId];
  } else {
    overrides[sessionId] = nextEntry;
  }
  persist();
  emit();
}

export function setPinned(sessionId, pinned) {
  mutate(sessionId, { pinned: !!pinned });
}

export function togglePinned(sessionId) {
  setPinned(sessionId, !(overrides[sessionId] && overrides[sessionId].pinned));
}

/** Rename. A blank/whitespace title clears the override (falls back to the original title). */
export function setTitle(sessionId, title) {
  const trimmed = typeof title === 'string' ? title.trim() : '';
  mutate(sessionId, { title: trimmed || null });
}

/** Forget everything for a conversation — used when it is deleted, so no orphan override lingers. */
export function forget(sessionId) {
  if (!overrides[sessionId]) return;
  delete overrides[sessionId];
  persist();
  emit();
}

/**
 * Overlay overrides onto a conversation list. Pure: returns a NEW list where each item carries its
 * effective `title`, an `originalTitle` (so the rename dialog can show what it is overriding), and a
 * `pinned` flag. Does not sort — the screen owns pinned-first ordering + date grouping.
 */
export function applyOverrides(list, snapshot = overrides) {
  if (!Array.isArray(list)) return [];
  return list.map((c) => {
    const o = snapshot[c.sessionId] || snapshot[c.id];
    if (!o) return { ...c, pinned: false, originalTitle: c.title };
    return {
      ...c,
      originalTitle: c.title,
      title: o.title != null ? o.title : c.title,
      pinned: !!o.pinned,
    };
  });
}

/** Test/dev seam, mirroring queue.__reset. */
export function __reset() {
  scope = 'anon';
  overrides = {};
  loadedScope = null;
  listeners.clear();
}
