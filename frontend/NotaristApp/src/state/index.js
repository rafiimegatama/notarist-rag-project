// Modular app state. Each domain is its OWN provider + hook in its own file — there is deliberately
// no single global store. This module only *composes* the six providers into one wrapper mounted
// around the authenticated app, so screens can consume exactly the slice they need.
import React, { useEffect, useRef } from 'react';
import useUser from '../hooks/useUser';
import { setCacheScope, clearAll } from '../services/cache';
import { setQueueScope } from '../services/mutationQueue';
import { setOverridesScope } from '../services/conversationOverrides';
import { startAppResumeWatch } from '../services/polling';
import { DashboardProvider, useDashboard } from './DashboardContext';
import { CaseProvider, useCases } from './CaseContext';
import { BundleProvider, useBundles } from './BundleContext';
import { ReminderProvider, useReminders } from './ReminderContext';
import { SearchProvider, useSearch } from './SearchContext';
import { ConversationProvider, useConversations } from './ConversationContext';
import { SyncProvider, useSync } from './SyncContext';

// Order is independent — no slice depends on another's context.
const PROVIDERS = [
  DashboardProvider,
  CaseProvider,
  BundleProvider,
  ReminderProvider,
  SearchProvider,
  ConversationProvider,
  // Owns the offline mutation queue's flush triggers. Independent of the others like every slice
  // here, but it must sit INSIDE CacheScopeBinder: it restores the persisted queue on mount, and the
  // queue is scoped per user just as the cache is.
  SyncProvider,
];

/**
 * Binds the offline cache to the signed-in user, and clears it when they leave (Sprint 4, Task 3).
 *
 * This lives here rather than in the auth layer for two reasons. The obvious one: this sprint does
 * not modify authentication. The better one: this component is mounted by AppStack, which the
 * navigator renders ONLY while `user` is truthy — so its lifetime already IS the session, exactly
 * the boundary the cache needs. Signing out unmounts the authenticated stack, the cleanup runs, and
 * the cached case list goes with it. Auth is read (useUser), never touched.
 *
 * Why bother: the cache holds debtor names and case numbers, and a notary office device is shared.
 * Data at rest after sign-out, readable by whoever logs in next, is not something to leave for later.
 * `setCacheScope` also purges any other user's cache on switch, so the two mechanisms cover each
 * other if one is somehow missed.
 *
 * Process death is NOT a sign-out and runs no cleanup — which is what keeps the cache useful across
 * an app restart. That asymmetry is the whole point.
 */
function CacheScopeBinder({ children }) {
  const user = useUser();
  const userId = user ? user.userId : null;

  // Bound DURING RENDER, deliberately, not in an effect.
  //
  // React runs child effects before parent effects. In an effect, this would set the scope only
  // AFTER every provider below had already fired its mount read — so the first cached read of the
  // session would look in the 'anon' scope, miss, and then have its memory tier cleared out from
  // under it by the scope change. Binding here happens before any child renders, let alone reads.
  //
  // setCacheScope is idempotent (it early-returns when the scope is unchanged), so a StrictMode
  // double-render or a concurrent re-render costs nothing.
  const bound = useRef(undefined);
  if (bound.current !== userId) {
    bound.current = userId;
    setCacheScope(userId);
    // Same binding, same reason, same moment — but the queue's stake is higher than the cache's. A
    // pending write flushed under the wrong session would post one notary's approval as another's,
    // so the scope has to be set before SyncProvider (a child) mounts and restores. Parent render
    // runs before child effects, which is exactly what this placement buys.
    setQueueScope(userId);
    // Same reason, lower stakes: a notary's private pins/renames for conversations are scoped to
    // them, so another login on a shared device never sees them. Overrides are NOT cleared on
    // sign-out (unlike the cache) — scoping already isolates them, and losing a user's pins because
    // their session ended would be the queue's mistake, not the cache's.
    setOverridesScope(userId);
  }

  useEffect(() => {
    return () => {
      // Sign-out (or session end). Fire-and-forget: the tree is going away and nothing awaits us.
      //
      // The CACHE goes; the mutation QUEUE deliberately does not. The cache is refetchable and holds
      // debtor names on a shared office device, so leaving it is a privacy problem. A queued mutation
      // is the opposite on both counts: it exists nowhere else, and deleting it would silently
      // discard a notary's decision because their session timed out. It stays on disk under their
      // scope and flushes when they sign back in.
      clearAll();
    };
  }, []);

  // App-resume staleness check for the polled resources (services/polling.js, Sprint 5 Task 5).
  // The watcher existed but nothing ever STARTED it, so backgrounding the app and reopening it later
  // never refreshed the dashboard/reminder/conversation data a notary was about to read — only the
  // mutation queue had a resume trigger (SyncContext). Started here because this component's lifetime
  // is the session: resources are only registered by providers below, and the watcher's own rule
  // ("resources nobody is watching are skipped") makes it inert while no screen subscribes.
  useEffect(() => startAppResumeWatch(), []);

  return children;
}

// Nests the providers via reduce instead of hand-writing six levels of JSX. The cache scope is bound
// OUTSIDE every provider, so it is set before any of them issues its first cached read.
export function AppStateProviders({ children }) {
  return (
    <CacheScopeBinder>
      {PROVIDERS.reduceRight((acc, Provider) => <Provider>{acc}</Provider>, children)}
    </CacheScopeBinder>
  );
}

export { useDashboard, useCases, useBundles, useReminders, useSearch, useConversations, useSync };
