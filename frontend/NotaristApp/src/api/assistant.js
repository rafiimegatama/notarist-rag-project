import client from './client';

export async function askAssistant(query, sessionId, options = {}) {
  const response = await client.post('/assistant/ask', {
    rawQuery: query,
    sessionId,
    safetyMode: options.safetyMode ?? true,
    maxContextTokens: options.maxContextTokens ?? 4096,
    classificationFilter: options.classificationFilter ?? null,
  });
  return response.data.data;
}

export async function getConversationHistory(sessionId, page = 0, size = 20) {
  const response = await client.get(`/assistant/history/${sessionId}`, {
    params: { page, size },
  });
  return response.data;
}
