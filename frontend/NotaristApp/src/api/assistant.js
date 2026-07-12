import client from './client';

export async function askAssistant(query, sessionId, options = {}) {
  const response = await client.post('/assistant/ask', {
    rawQuery: query,
    sessionId,
    safetyMode: options.safetyMode ?? 'STRICT',
    contextTokenBudget: options.contextTokenBudget ?? 3072,
    maxClassificationLevel: options.maxClassificationLevel ?? null,
    documentTypeFilter: options.documentTypeFilter ?? null,
    maxResults: options.maxResults ?? 10,
  });
  if (response.data.status !== 'SUCCESS') {
    const error = new Error(response.data.errorMessage || 'Assistant request failed');
    error.response = response;
    throw error;
  }
  return response.data.data;
}

export async function getConversationHistory(sessionId) {
  const response = await client.get(`/assistant/history/${sessionId}`);
  return response.data.data;
}
