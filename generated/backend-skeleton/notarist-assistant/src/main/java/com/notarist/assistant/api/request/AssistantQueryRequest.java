package com.notarist.assistant.api.request;

/**
 * @deprecated Use {@link AssistantRequest} instead.
 * This skeleton draft used different field names (queryText, topK, searchIntent)
 * that diverged from the implementation contract (rawQuery, maxResults, safetyMode).
 * Removed in PHASE 6A.3-FIX — do not add new consumers.
 */
@Deprecated(since = "6A.3-FIX", forRemoval = true)
public record AssistantQueryRequest() {}
