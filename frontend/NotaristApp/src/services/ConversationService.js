// ConversationService — the Assistant conversation list + delete. Delegates to api/assistant
// (mock|http behind FEATURES.conversationListEndpoint). Per-session history stays in the assistant api.
import { FEATURES } from '../constants/config';
import { listConversations, deleteConversation } from '../api/assistant';

export const ConversationService = {
  usingMock: !FEATURES.conversationListEndpoint,
  list: () => listConversations(),
  remove: (sessionId) => deleteConversation(sessionId),
};
