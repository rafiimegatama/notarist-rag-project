import client from './client';
import { FEATURES } from '../constants/config';
import { mock, requireEndpoint } from './_support';
import { unwrapOrThrow, unwrap, toList } from './envelope';
import { MOCK_CONVERSATIONS } from '../mocks/fixtures';
import { normalizeConversation } from '../models/Conversation';

export async function askAssistant(query, sessionId, options = {}) {
  // Sprint 5, Task 10: previously ungated (it lived in the dead `LIVE` block). There is no mock
  // path and there must not be one — a fabricated legal answer is the most harmful thing this app
  // could render, banner or no banner.
  requireEndpoint(FEATURES.assistantEndpoint, 'assistant/ask');
  const response = await client.post('/assistant/ask', {
    rawQuery: query,
    sessionId,
    safetyMode: options.safetyMode ?? 'STRICT',
    contextTokenBudget: options.contextTokenBudget ?? 3072,
    maxClassificationLevel: options.maxClassificationLevel ?? null,
    documentTypeFilter: options.documentTypeFilter ?? null,
    maxResults: options.maxResults ?? 10,
  });
  // Was a bare `new Error(...)`: no `kind`, so it bypassed the whole Sprint-4 error layer —
  // <ErrorState> could not classify it and retry could not reason about it (Sprint 5, Task 1).
  return unwrapOrThrow(response, null);
}

export async function getConversationHistory(sessionId) {
  requireEndpoint(FEATURES.assistantEndpoint, 'assistant/history');
  const response = await client.get(`/assistant/history/${sessionId}`);
  // `response.data.data` would throw a TypeError on a proxy/gateway body that is not the envelope.
  return unwrap(response, null);
}

// --- Conversation list (Conversation History screen) ---
// The backend only exposes history for a KNOWN sessionId; there is no "list all conversations"
// endpoint yet (FEATURES.conversationListEndpoint). Until then we serve marked mock summaries.
// getConversationHistory above is the real call used once a conversation is opened.
export async function listConversations() {
  if (FEATURES.conversationListEndpoint) {
    const response = await client.get('/assistant/conversations');
    // toList/unwrap rather than `response.data.data?.items ?? []`. This route does not exist yet, so
    // its payload shape is UNKNOWN — which is exactly why guessing `{items}` and swallowing anything
    // else with `?? []` is the wrong bet: that is the shape assumption that made listBundles return
    // zero bundles in silence. toList accepts a bare array or a paged wrapper, so whichever shape the
    // endpoint ships with, this reads it or renders an honest empty — never a silent one.
    return toList(unwrap(response, []), ['items', 'content']).map(normalizeConversation);
  }
  return mock(MOCK_CONVERSATIONS.map(normalizeConversation), { label: 'conversations' });
}

export async function deleteConversation(sessionId) {
  if (FEATURES.conversationListEndpoint) {
    const response = await client.delete(`/assistant/conversations/${sessionId}`);
    return unwrap(response, null);
  }
  return mock({ sessionId, deleted: true }, { label: 'conversation-delete', delay: 250 });
}
