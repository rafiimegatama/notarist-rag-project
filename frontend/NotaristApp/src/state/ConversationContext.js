// Conversation State — the list of past Assistant conversations for the Conversation History screen.
// Delete removes optimistically and calls the API (mock until the backend list/delete endpoints ship).
//
// Sprint 4: load/refresh moved to useCachedResource (cache-first + background refresh, Tasks 3+4).
// The optimistic delete stays here; it owns its own rollback and is not a load concern.
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { ConversationService } from '../services';
import useCachedResource from '../hooks/useCachedResource';
import { useResourceRegistration, useMarkFetched } from '../hooks/usePolledResource';
import { CacheKeys } from '../services/cache';
import * as cache from '../services/cache';
import { optimistic, INVALIDATES } from '../services/mutations';
import * as overridesStore from '../services/conversationOverrides';

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

  const rawConversations = data || EMPTY;

  // Local pin/rename overlay (no backend — see services/conversationOverrides). Subscribed once; the
  // store persists per-user and emits on every change, which re-renders the list with new titles/pins.
  const [overrides, setOverrides] = useState(() => overridesStore.getSnapshot());
  useEffect(() => {
    const unsub = overridesStore.subscribe(setOverrides);
    overridesStore.restore();
    return unsub;
  }, []);

  // Effective list: raw conversations with their local title/pin applied. Memoised so grouping
  // downstream only re-runs when the list or an override actually changes.
  const conversations = useMemo(
    () => overridesStore.applyOverrides(rawConversations, overrides),
    [rawConversations, overrides],
  );

  useResourceRegistration('conversations', refresh);
  useMarkFetched('conversations', lastSyncedAt);

  // Mirrors the RAW list for the rollback path without making `remove` depend on `conversations`
  // (which would rebuild the callback — and every memo holding it — on every list change). Raw, not
  // overlaid: setData/cache.write must persist server-shaped rows, never the pin/title overlay fields.
  const listRef = useRef(rawConversations);
  listRef.current = rawConversations;

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
    // The write is gone; drop any local pin/rename for it so a re-created session with the same id
    // (or a stale disk copy) cannot resurrect an override that no longer has a conversation.
    overridesStore.forget(sessionId);
    return result.data;
  }, [setData]);

  // Pin/rename are LOCAL preferences with no server call — they mutate the overlay store, which
  // persists and emits. Stable callbacks (no dependency on the list), so memoised rows keep identity.
  const togglePin = useCallback((sessionId) => overridesStore.togglePinned(sessionId), []);
  const rename = useCallback((sessionId, title) => overridesStore.setTitle(sessionId, title), []);

  const value = useMemo(
    () => ({
      conversations, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
      reload, refresh, remove, togglePin, rename,
    }),
    [conversations, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
      reload, refresh, remove, togglePin, rename],
  );

  return <ConversationContext.Provider value={value}>{children}</ConversationContext.Provider>;
}

export function useConversations() {
  const ctx = useContext(ConversationContext);
  if (!ctx) throw new Error('useConversations must be used within ConversationProvider');
  return ctx;
}
