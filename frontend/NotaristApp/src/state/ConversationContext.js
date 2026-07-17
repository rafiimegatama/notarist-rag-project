// Conversation State — the list of past Assistant conversations for the Conversation History screen.
// Delete removes optimistically and calls the API (mock until the backend list/delete endpoints ship).
//
// Sprint 4: load/refresh moved to useCachedResource (cache-first + background refresh, Tasks 3+4).
// The optimistic delete stays here; it owns its own rollback and is not a load concern.
import React, { createContext, useCallback, useContext, useMemo, useRef } from 'react';
import { ConversationService } from '../services';
import useCachedResource from '../hooks/useCachedResource';
import { useResourceRegistration, useMarkFetched } from '../hooks/usePolledResource';
import { CacheKeys } from '../services/cache';
import * as cache from '../services/cache';
import { optimistic, INVALIDATES } from '../services/mutations';

const ConversationContext = createContext(null);

// This service reports mock status on the service object rather than tagging the response, so the
// default `__mock` sniff would miss it and the hook would cache fixtures. Read the service instead.
const deriveMock = () => ConversationService.usingMock;

const EMPTY = [];

export function ConversationProvider({ children }) {
  const {
    data, setData, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
    reload, refresh,
  } = useCachedResource({
    key: CacheKeys.CONVERSATIONS,
    fetcher: () => ConversationService.list(),
    deriveMock,
  });

  const conversations = data || EMPTY;

  useResourceRegistration('conversations', refresh);
  useMarkFetched('conversations', lastSyncedAt);

  // Mirrors the list for the rollback path without making `remove` depend on `conversations` (which
  // would rebuild the callback — and every memo holding it — on every list change).
  const listRef = useRef(conversations);
  listRef.current = conversations;

  // Sprint 5 (Task 3): the hand-rolled optimistic delete now goes through services/mutations, so it
  // shares one rollback/conflict/invalidation policy with verification, OCR and status changes
  // instead of being a fourth private implementation of the same dance.
  //
  // Still throws on failure: ConversationsScreen catches it to raise "Tidak dapat menghapus
  // percakapan". optimistic() resolves rather than throwing, so the throw is re-raised here — the
  // screen's contract is unchanged, which is the point of a no-UI-rewrite sprint.
  const remove = useCallback(async (sessionId) => {
    const result = await optimistic({
      apply: () => {
        const prev = listRef.current;
        setData(prev.filter((c) => c.sessionId !== sessionId));
        return prev;
      },
      rollback: (prev) => setData(prev),
      commit: () => ConversationService.remove(sessionId),
      settle: () => {
        // Keep the cache consistent with the screen. Without this, deleting a conversation and
        // reopening the app offline would resurrect it from disk.
        if (!ConversationService.usingMock) cache.write(CacheKeys.CONVERSATIONS, listRef.current);
      },
      invalidates: INVALIDATES.conversation,
    });
    if (!result.ok) throw result.error;
    return result.data;
  }, [setData]);

  const value = useMemo(
    () => ({
      conversations, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
      reload, refresh, remove,
    }),
    [conversations, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
      reload, refresh, remove],
  );

  return <ConversationContext.Provider value={value}>{children}</ConversationContext.Provider>;
}

export function useConversations() {
  const ctx = useContext(ConversationContext);
  if (!ctx) throw new Error('useConversations must be used within ConversationProvider');
  return ctx;
}
