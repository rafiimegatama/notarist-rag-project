// Lightweight offline cache (Sprint 4, Task 3).
//
// Two tiers, and the split is the whole design:
//
//   memory — a Map. Synchronous, so a cached screen paints on its FIRST render with no flash of
//            skeleton. This tier is what makes Task 4 (show cache, refresh behind it) feel instant.
//   disk   — expo-file-system, so the cache survives an app restart. That is the tier that makes it
//            an OFFLINE cache rather than a session cache: a notary reopening the app on a train
//            sees their case list.
//
// Not Redux, not a Context, not global UI state: this is a service with a get/set API, sitting beside
// the other services. Contexts call it; nothing subscribes to it.
//
// ---------------------------------------------------------------------------------------------
// WHY Paths.cache AND NOT Paths.document
//
// This cache holds notarial data — debtor names, banks, case numbers. Two properties of the cache
// directory matter for that:
//   * it is NOT included in iCloud/iTunes backups, so cached debtor data does not silently leave the
//     device inside a user's personal backup;
//   * the OS may evict it under storage pressure, which is correct for data we can always refetch.
// Paths.document has the opposite of both properties. For this payload that would be the wrong trade.
//
// WHY NOT utils/storage.js — it wraps expo-secure-store, which is for small secrets. Android's
// SecureStore rejects values over ~2KB; a cached case list blows straight past that. Encrypting a
// cache would also cost a KeyStore round-trip on every read, defeating the point of the memory tier.
//
// PLATFORM: expo-file-system has no web implementation in SDK 57 (its web module only console.warns).
// This app builds for web (`expo export --platform web`), so every disk call is guarded and web
// degrades to memory-only — same graceful-degradation contract as utils/storage.js. On web the cache
// is per-session; nothing breaks, offline-across-restart simply does not apply.
// ---------------------------------------------------------------------------------------------

import { Platform } from 'react-native';
import { File, Directory, Paths } from 'expo-file-system';

// Bump when the stored shape changes. Entries written by an older app version are ignored rather
// than parsed into a screen — a stale shape is a crash waiting to happen after a refactor.
const CACHE_VERSION = 2;
const ROOT_DIR = 'api-cache';

// Disk is unavailable on web; every disk path no-ops there and the memory tier carries the feature.
const DISK_AVAILABLE = Platform.OS !== 'web';

/** Cache keys. Centralised so a typo cannot silently split one cache into two. */
export const CacheKeys = {
  DASHBOARD: 'dashboard-summary',
  STATISTICS: 'case-statistics',
  REMINDERS: 'reminder-list',
  CASE_LIST: 'case-list',
  CONVERSATIONS: 'conversation-list',
  SEARCH_RECENT: 'search-recent',
};

// memory tier: key -> { data, storedAt }
const memory = new Map();

// Scope isolates one signed-in user's cache from another's on a shared device — a real scenario in a
// notary office. Set from state/index.js, which reads the auth context read-only.
let scope = 'anon';

function scopeDirName() {
  // A userId is a UUID, but never trust it into a path: anything outside [A-Za-z0-9_-] could escape
  // the directory (`../`) or collide.
  return String(scope).replace(/[^A-Za-z0-9_-]/g, '') || 'anon';
}

function memKey(key) {
  return `${scopeDirName()}:${key}`;
}

function rootDir() {
  return new Directory(Paths.cache, ROOT_DIR, `v${CACHE_VERSION}`);
}

function scopeDir() {
  return new Directory(rootDir(), scopeDirName());
}

function entryFile(key) {
  // Keys are internal constants, but sanitise anyway — same reasoning as scopeDirName.
  const safe = String(key).replace(/[^A-Za-z0-9_-]/g, '');
  return new File(scopeDir(), `${safe}.json`);
}

/**
 * Point the cache at a user. Purges every OTHER user's cached data from disk, so signing in as B
 * cannot leave A's debtor names one render away from B's screen.
 *
 * Called from AppStateProviders, which mounts only inside the authenticated stack.
 */
export function setCacheScope(userId) {
  const next = userId ? String(userId) : 'anon';
  if (next === scope) return;
  scope = next;
  // The memory tier is not namespaced by directory, so drop it wholesale on a scope change.
  memory.clear();
  purgeOtherScopes();
}

export function getCacheScope() {
  return scope;
}

// Deletes sibling scope directories. Best-effort and silent: a cache that cannot clean up must not
// break the app, and there is no user-actionable outcome.
function purgeOtherScopes() {
  if (!DISK_AVAILABLE) return;
  try {
    const root = rootDir();
    if (!root.exists) return;
    const keep = scopeDirName();
    for (const child of root.list()) {
      try {
        if (child instanceof Directory && child.name !== keep) child.delete();
      } catch (_) {
        /* one stubborn directory must not abort the sweep */
      }
    }
  } catch (_) {
    /* ignore */
  }
}

/**
 * Read a cached entry. Returns null on a miss, a bad parse, or a version mismatch.
 * @returns {Promise<{data:any, storedAt:number, age:number} | null>}
 */
export async function read(key) {
  const mk = memKey(key);
  const hit = memory.get(mk);
  if (hit) return { data: hit.data, storedAt: hit.storedAt, age: Date.now() - hit.storedAt };

  if (!DISK_AVAILABLE) return null;
  try {
    const file = entryFile(key);
    if (!file.exists) return null;
    const parsed = JSON.parse(await file.text());
    if (!parsed || parsed.version !== CACHE_VERSION || typeof parsed.storedAt !== 'number') {
      return null;
    }
    // Promote into memory so the next read this session is synchronous.
    memory.set(mk, { data: parsed.data, storedAt: parsed.storedAt });
    return { data: parsed.data, storedAt: parsed.storedAt, age: Date.now() - parsed.storedAt };
  } catch (_) {
    // Corrupt or unreadable entry behaves as a miss: the caller refetches.
    return null;
  }
}

/** Synchronous memory-only peek. Lets a component render cached data on its first frame. */
export function peek(key) {
  const hit = memory.get(memKey(key));
  if (!hit) return null;
  return { data: hit.data, storedAt: hit.storedAt, age: Date.now() - hit.storedAt };
}

/** Store an entry. Best-effort: a failed write is a slower app, never a broken one. */
export async function write(key, data) {
  if (data === undefined) return;
  const storedAt = Date.now();
  memory.set(memKey(key), { data, storedAt });

  if (!DISK_AVAILABLE) return;
  try {
    const dir = scopeDir();
    if (!dir.exists) dir.create({ intermediates: true, idempotent: true });
    const file = entryFile(key);
    if (!file.exists) file.create({ intermediates: true, overwrite: true });
    // File.write is synchronous. Entries here are small (a summary, one page of a list), so this is
    // a sub-millisecond hop; it is the reason nothing bulky should ever be cached through this API.
    file.write(JSON.stringify({ version: CACHE_VERSION, storedAt, data }));
  } catch (_) {
    // Disk full, evicted directory, unsupported platform — the memory tier still holds the entry.
  }
}

export async function remove(key) {
  memory.delete(memKey(key));
  if (!DISK_AVAILABLE) return;
  try {
    const file = entryFile(key);
    if (file.exists) file.delete();
  } catch (_) {
    /* ignore */
  }
}

/**
 * Drop everything for the current scope, memory and disk.
 *
 * Called when the authenticated stack unmounts — i.e. on sign-out. A signed-out device must not keep
 * a readable copy of a notary's case list. This is invoked from AppStateProviders' effect cleanup
 * rather than from the auth layer, which this sprint does not modify.
 */
export async function clearAll() {
  memory.clear();
  if (!DISK_AVAILABLE) return;
  try {
    const dir = scopeDir();
    if (dir.exists) dir.delete();
  } catch (_) {
    /* ignore */
  }
}
