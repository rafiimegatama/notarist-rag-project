// Modular app state. Each domain is its OWN provider + hook in its own file — there is deliberately
// no single global store. This module only *composes* the six providers into one wrapper mounted
// around the authenticated app, so screens can consume exactly the slice they need.
import React, { useEffect, useRef } from 'react';
import useUser from '../hooks/useUser';
import { setCacheScope, clearAll } from '../services/cache';
import { DashboardProvider, useDashboard } from './DashboardContext';
import { CaseProvider, useCases } from './CaseContext';
import { BundleProvider, useBundles } from './BundleContext';
import { ReminderProvider, useReminders } from './ReminderContext';
import { SearchProvider, useSearch } from './SearchContext';
import { ConversationProvider, useConversations } from './ConversationContext';

// Order is independent — no slice depends on another's context.
const PROVIDERS = [
  DashboardProvider,
  CaseProvider,
  BundleProvider,
  ReminderProvider,
  SearchProvider,
  ConversationProvider,
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
  }

  useEffect(() => {
    return () => {
      // Sign-out (or session end). Fire-and-forget: the tree is going away and nothing awaits us.
      clearAll();
    };
  }, []);

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

export { useDashboard, useCases, useBundles, useReminders, useSearch, useConversations };
