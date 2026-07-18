// Offline mutation queue (Sprint 1) — the durable half of services/mutations.js#optimistic.
//
// optimistic() already answers "what happens to the SCREEN when a write fails": roll back, always,
// because a notary must never look at an approval that did not land. This module answers the other
// half: "what happens to the WRITE". A rolled-back mutation used to be gone — the notary retyped it,
// or lost it. Now it is queued, survives restart, and replays when the app can reach the server.
//
// ---------------------------------------------------------------------------------------------
// THE RULE THAT SHAPES EVERYTHING HERE: NEVER DUPLICATE A POST
//
// A queue that replays anything it failed to confirm will eventually record a notary's approval
// twice. So entries are only ever auto-replayed when the request PROVABLY NEVER REACHED THE SERVER.
// That is knowable, and api/errors.js#classifyNetworkError already knows it:
//
//   UNREACHABLE  ECONNREFUSED / ENOTFOUND / EHOSTUNREACH / ERR_NETWORK
//                The connection was refused or the host never resolved. Nothing was delivered.
//                SAFE to replay.
//   OFFLINE      A request was created and never answered, with no code to explain why.
//                SAFE to replay: axios could not hand it to a socket.
//
//   TIMEOUT      Sent, no answer within the deadline. The server may have applied it and been slow
//                to say so. NOT safe — replaying is how you get two of something.
//   any HTTP status (4xx/5xx)
//                The server answered. It received the request. A 500 may well have committed the
//                row before failing to serialise the response. NOT safe.
//
// Unsafe outcomes are not discarded — "never lose mutations" — they are parked as FAILED with the
// reason, and a human decides. That is the same division of labour mutations.js already states: the
// machine retries transport, a person retries intent.
//
// ---------------------------------------------------------------------------------------------
// WHY REQUESTS ARE DATA, NOT CLOSURES
//
// A queued mutation must survive process death, so it cannot hold a function. Every entry stores
// { method, url, body } and is replayed through the same api/client as a live call — same auth
// interceptor, same envelope handling, same error classification. Nothing in this file knows what a
// verification decision is; it moves requests.
//
// WHY Paths.document AND NOT services/cache.js
//
// cache.js is deliberately in Paths.cache, which "the OS may evict under storage pressure — correct
// for data we can always refetch". A pending mutation is the one thing in this app that CANNOT be
// refetched: it exists nowhere else until it lands. A queue the OS may delete is not a queue, so this
// takes the opposite trade for the opposite reason. It is the same trade cache.js makes explicit, in
// the other direction, and it costs the entries being included in device backups: they hold ids and
// decisions (an itemId, "APPROVED"), never names or document content.
// ---------------------------------------------------------------------------------------------

import { Platform } from 'react-native';
import { File, Directory, Paths } from 'expo-file-system';
import client from '../api/client';
import { normalizeError, ErrorKind, messageForKind } from '../api/errors';
import { invalidateKeys, INVALIDATES, optimistic } from './mutations';

const QUEUE_VERSION = 1;
const ROOT_DIR = 'mutation-queue';
const DISK_AVAILABLE = Platform.OS !== 'web';

/** Max automatic transport attempts before an entry parks as FAILED and waits for a human. */
export const MAX_ATTEMPTS = 5;

export const QueueStatus = {
  PENDING: 'pending',       // waiting for a flush
  IN_FLIGHT: 'inFlight',    // being sent right now
  FAILED: 'failed',         // parked: needs a person (retry or cancel)
};

/**
 * Failure classes an entry can park with. Kept distinct because the UI must say WHY, and because
 * only one of them is the app's fault.
 */
export const FailureReason = {
  CONFLICT: 'conflict',       // 409/412/422 — the server's copy wins; replaying cannot help
  SERVER: 'server',           // 5xx — may or may not have landed; a human must check
  UNSAFE_REPLAY: 'unsafe',    // timeout — outcome unknown, replay could duplicate
  EXHAUSTED: 'exhausted',     // ran out of transport attempts while offline
  REJECTED: 'rejected',       // 4xx that is not a conflict (e.g. 403/404) — will never succeed
};

// ---------------------------------------------------------------------------------------------
// state — a module singleton, matching api/connectivity.js. This is transport-adjacent machinery,
// not application state; screens reach it through state/SyncContext, which is the only subscriber
// that re-renders anything.
// ---------------------------------------------------------------------------------------------

let entries = [];          // ordered oldest-first; order IS the replay order
let flushing = false;      // single-flight lock — two flushes would double-send
let lastFlushAt = null;
let scope = 'anon';
let restored = false;

const listeners = new Set();

function emit() {
  const snapshot = getQueueState();
  for (const listener of Array.from(listeners)) {
    try {
      listener(snapshot);
    } catch (_) {
      // One bad subscriber must not stop the others from hearing about the queue.
    }
  }
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** The whole queue, safe to render. Entries are copied so a consumer cannot mutate the real one. */
export function getQueueState() {
  return {
    entries: entries.map((e) => ({ ...e })),
    pendingCount: entries.filter((e) => e.status !== QueueStatus.FAILED).length,
    failedCount: entries.filter((e) => e.status === QueueStatus.FAILED).length,
    flushing,
    lastFlushAt,
  };
}

/**
 * Entries bucketed by resource ("verification", "ocrField", …) — the sprint's "queue grouped by
 * resource". Derived, never stored: one list is the source of truth for order, and a second copy
 * grouped differently is a second truth waiting to disagree with it.
 */
export function groupedByResource() {
  const groups = new Map();
  for (const e of entries) {
    if (!groups.has(e.resource)) groups.set(e.resource, []);
    groups.get(e.resource).push({ ...e });
  }
  return Array.from(groups, ([resource, items]) => ({ resource, items }));
}

// ---------------------------------------------------------------------------------------------
// persistence
// ---------------------------------------------------------------------------------------------

const scopeDirName = () => `scope-${scope}`;
const rootDir = () => new Directory(Paths.document, ROOT_DIR);
const queueFile = () => new File(new Directory(Paths.document, ROOT_DIR, scopeDirName()), 'queue.json');

/** Scope the queue to a user, mirroring cache.setCacheScope — one notary's pending writes must never
 *  flush under another's token on a shared office device. */
export async function setQueueScope(userId) {
  const next = userId ? String(userId) : 'anon';
  if (next === scope) return;
  scope = next;
  entries = [];
  restored = false;
  await restore();
}

/** Best-effort persist. A queue that cannot write to disk is still a working in-memory queue for
 *  this session; failing the caller's mutation over it would be the wrong trade. */
async function persist() {
  if (!DISK_AVAILABLE) return;
  try {
    const dir = new Directory(Paths.document, ROOT_DIR, scopeDirName());
    if (!dir.exists) dir.create({ intermediates: true, idempotent: true });
    const file = queueFile();
    if (!file.exists) file.create({ intermediates: true, overwrite: true });
    // IN_FLIGHT is a runtime state, not a durable one: if the process dies mid-send we cannot know
    // whether it landed, so it is persisted as FAILED/UNSAFE_REPLAY rather than as PENDING. Writing
    // it back as PENDING is exactly how a restart would duplicate a POST.
    const durable = entries.map((e) =>
      e.status === QueueStatus.IN_FLIGHT
        ? { ...e, status: QueueStatus.FAILED, failure: FailureReason.UNSAFE_REPLAY,
            failureMessage: 'Aplikasi tertutup saat mengirim. Status di server tidak diketahui.' }
        : e,
    );
    file.write(JSON.stringify({ version: QUEUE_VERSION, entries: durable }));
  } catch (_) {
    /* disk full / unsupported — the in-memory queue carries this session */
  }
}

/** Load the queue from disk. Idempotent; called on app start and on scope change. */
export async function restore() {
  if (restored) return getQueueState();
  restored = true;
  if (!DISK_AVAILABLE) return getQueueState();
  try {
    const file = queueFile();
    if (!file.exists) return getQueueState();
    const parsed = JSON.parse(await file.text());
    if (!parsed || parsed.version !== QUEUE_VERSION || !Array.isArray(parsed.entries)) {
      return getQueueState();
    }
    entries = parsed.entries;
    emit();
  } catch (_) {
    // A corrupt queue file is the one thing we must not silently drop, but we also cannot replay what
    // we cannot parse. Leave the file in place for support and continue with an empty queue.
  }
  return getQueueState();
}

// ---------------------------------------------------------------------------------------------
// enqueue / cancel / retry
// ---------------------------------------------------------------------------------------------

let seq = 0;
const nextId = () => `m${Date.now().toString(36)}-${(seq += 1).toString(36)}`;

/**
 * Queue a mutation for later replay.
 *
 * @param {Object} spec
 * @param {string} spec.resource     grouping key, e.g. 'verification' | 'ocrField' | 'caseStatus'.
 *                                   Use the same names as mutations.INVALIDATES so the flush can
 *                                   invalidate the right caches without a second table.
 * @param {string} [spec.resourceId] the thing being written (bundleId, documentId…), for grouping.
 * @param {string} spec.label        human copy for the inspector, e.g. "Setujui NIK".
 * @param {{method:string,url:string,body?:any}} spec.request  replayed verbatim through api/client.
 * @param {string} [spec.dedupeKey]  two entries with the same key are the same intent; the newer
 *                                   REPLACES the older. Without this, deciding a checklist item
 *                                   twice offline would send two POSTs for one item.
 */
export async function enqueue({ resource, resourceId = null, label, request, dedupeKey = null }) {
  const entry = {
    id: nextId(),
    resource,
    resourceId,
    label: label || resource,
    request,
    dedupeKey,
    status: QueueStatus.PENDING,
    attempts: 0,
    queuedAt: Date.now(),
    lastAttemptAt: null,
    failure: null,
    failureMessage: null,
  };

  if (dedupeKey) {
    // Last write wins, and it keeps its place in the queue rather than jumping to the back: the
    // ORDER of a notary's decisions is not something to reshuffle because they changed their mind.
    const at = entries.findIndex((e) => e.dedupeKey === dedupeKey && e.status !== QueueStatus.IN_FLIGHT);
    if (at !== -1) {
      entry.queuedAt = entries[at].queuedAt;
      entries[at] = entry;
      await persist();
      emit();
      return entry.id;
    }
  }

  entries.push(entry);
  await persist();
  emit();
  return entry.id;
}

/** Drop a queued mutation. The one operation that legitimately loses a write — a person asked. */
export async function cancel(id) {
  const at = entries.findIndex((e) => e.id === id);
  // Refuse to cancel mid-flight: the request is already on the wire and the server's answer is about
  // to arrive. Removing the entry now would lose the outcome without preventing the write.
  if (at === -1 || entries[at].status === QueueStatus.IN_FLIGHT) return false;
  entries.splice(at, 1);
  await persist();
  emit();
  return true;
}

/** Re-arm a FAILED entry. This is the human "retry intent" the auto-flush deliberately refuses. */
export async function retry(id) {
  const entry = entries.find((e) => e.id === id);
  if (!entry || entry.status !== QueueStatus.FAILED) return false;
  entry.status = QueueStatus.PENDING;
  entry.attempts = 0;
  entry.failure = null;
  entry.failureMessage = null;
  await persist();
  emit();
  return true;
}

// ---------------------------------------------------------------------------------------------
// flush
// ---------------------------------------------------------------------------------------------

/** Transport failures that prove nothing was delivered — the only auto-replayable class. */
const REPLAYABLE = [ErrorKind.OFFLINE, ErrorKind.UNREACHABLE];

function classifyFailure(error) {
  const kind = error.kind;
  if (REPLAYABLE.indexOf(kind) !== -1) return null;            // stays PENDING, try again later
  if (kind === ErrorKind.TIMEOUT) return FailureReason.UNSAFE_REPLAY;
  if (kind === ErrorKind.CONFLICT || kind === ErrorKind.PRECONDITION_FAILED || kind === ErrorKind.VALIDATION) {
    return FailureReason.CONFLICT;
  }
  if (kind === ErrorKind.SERVER || kind === ErrorKind.UNAVAILABLE) return FailureReason.SERVER;
  return FailureReason.REJECTED;
}

function park(entry, failure, error) {
  entry.status = QueueStatus.FAILED;
  entry.failure = failure;
  // The kind's copy, not the server's raw errorMessage: this string is rendered to a notary, and
  // api/errors owns that vocabulary. The diagnostic keeps the technical detail for support.
  entry.failureMessage = error ? messageForKind(error.kind) : messageForKind(ErrorKind.UNKNOWN);
  entry.diagnostic = error ? error.diagnostic : null;
}

/**
 * Send everything that is PENDING, oldest first, stopping at the first transport failure.
 *
 * Sequential and single-flight, both deliberately:
 *   * parallel replay would race two writes to the same bundle, and the last response to land — not
 *     the last decision made — would win;
 *   * stopping at the first transport failure is what makes this an offline queue rather than a
 *     retry storm. If one request cannot reach the server, the next one cannot either; hammering all
 *     of them just burns battery and rewrites the same failure onto every entry.
 *
 * @returns {Promise<{sent:number, failed:number, remaining:number}>}
 */
export async function flush() {
  if (flushing) return { sent: 0, failed: 0, remaining: entries.length, skipped: true };
  const queued = entries.filter((e) => e.status === QueueStatus.PENDING);
  if (!queued.length) return { sent: 0, failed: 0, remaining: 0 };

  flushing = true;
  emit();

  let sent = 0;
  let failed = 0;
  const touchedResources = new Set();

  try {
    for (const entry of queued) {
      // Re-read: a cancel() may have removed it while an earlier entry was in flight.
      if (!entries.includes(entry)) continue;

      entry.status = QueueStatus.IN_FLIGHT;
      entry.attempts += 1;
      entry.lastAttemptAt = Date.now();
      // Record the attempt on disk BEFORE the request leaves. persist() maps IN_FLIGHT ->
      // FAILED/UNSAFE_REPLAY (see its body), so if the process dies during the await below, a restart
      // restores this entry as parked ("status unknown, a human decides") instead of PENDING. Without
      // this write the on-disk copy stays PENDING across the whole round-trip, and the crash-during-send
      // path auto-replays it on restart — the one double-POST this module exists to prevent. Best-effort
      // like every persist(): a disk that cannot be written still sends, it just loses this guarantee.
      await persist();
      emit();

      try {
        const { method, url, body } = entry.request;
        await client.request({ method, url, data: body });

        // Success: the entry has done its job and leaves the queue. Removed by identity, not index —
        // the array may have shifted under a concurrent cancel().
        const at = entries.indexOf(entry);
        if (at !== -1) entries.splice(at, 1);
        touchedResources.add(entry.resource);
        sent += 1;
      } catch (err) {
        const error = normalizeError(err);
        const failure = classifyFailure(error);

        if (failure === null) {
          // Transport: nothing was delivered. Keep it PENDING and stop — the network is down, and
          // every following entry would fail the same way.
          entry.status = QueueStatus.PENDING;
          if (entry.attempts >= MAX_ATTEMPTS) {
            park(entry, FailureReason.EXHAUSTED, error);
            failed += 1;
          }
          emit();
          break;
        }

        park(entry, failure, error);
        failed += 1;
        emit();
        // An HTTP answer proves the server is reachable, so the next entry is worth trying.
      }
    }
  } finally {
    flushing = false;
    lastFlushAt = Date.now();
    await persist();
    emit();
  }

  // Only after the writes land: the caches those resources feed are now stale. Reuses the same
  // INVALIDATES table the online path uses (mutations.js) rather than a second copy of that mapping.
  if (touchedResources.size) {
    const keys = new Set();
    for (const r of touchedResources) (INVALIDATES[r] || []).forEach((k) => keys.add(k));
    await invalidateKeys(Array.from(keys));
  }

  return { sent, failed, remaining: entries.filter((e) => e.status === QueueStatus.PENDING).length };
}

/** True when a failure proves the request never reached the server, so replaying cannot duplicate it. */
export function isReplayable(error) {
  return REPLAYABLE.indexOf(normalizeError(error).kind) !== -1;
}

/**
 * An optimistic mutation that survives being offline — the API screens should reach for.
 *
 * Composes services/mutations#optimistic (apply -> commit -> rollback/invalidate) with this queue,
 * and the composition order is the point:
 *
 *   the write fails on transport -> ROLL BACK the screen, THEN queue the request.
 *
 * Both, not either. Rolling back is not undone by queueing: until the server has accepted it, the
 * decision has not happened, and mutations.js is unambiguous about what the screen may claim in the
 * meantime ("worse than no optimistic update at all: the UI would be lying about a legal act"). So
 * the notary sees their approval revert AND sees it counted in the pending badge — "not yet", which
 * is true — rather than a checkmark that means nothing until the train leaves the tunnel.
 *
 * Lives here rather than in mutations.js to keep the dependency one-way: this module already imports
 * that one, and the reverse would close a cycle around two modules that both run on app start.
 *
 * @param {Object} spec              everything optimistic() takes, plus:
 * @param {Object} spec.queue        { resource, resourceId, label, request, dedupeKey } — see enqueue.
 *                                   `request` must be the SAME call `commit` makes, as plain data.
 * @returns {Promise<{ok:boolean, data?:any, error?:ApiError, rolledBack?:boolean, queued?:boolean}>}
 */
export async function optimisticQueued({ queue: queueSpec, ...spec }) {
  const result = await optimistic(spec);
  if (result.ok || !queueSpec) return result;

  // Only transport failures. A 409 means the server disagrees and replaying changes nothing; a 500
  // may already have landed. Queueing either would turn a visible failure into a silent duplicate.
  if (!isReplayable(result.error)) return result;

  await enqueue(queueSpec);
  return { ...result, queued: true };
}

/** Test/dev seam, mirroring connectivity.__reset. Not called by app code. */
export function __reset() {
  entries = [];
  flushing = false;
  lastFlushAt = null;
  restored = false;
  scope = 'anon';
  listeners.clear();
}
