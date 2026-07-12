package com.notarist.assistant.api.request;

public record AssistantRequest(
        String rawQuery,
        String sessionId,
        String maxClassificationLevel,
        String documentTypeFilter,
        String safetyMode,
        Integer maxResults,
        Integer contextTokenBudget
) {}
