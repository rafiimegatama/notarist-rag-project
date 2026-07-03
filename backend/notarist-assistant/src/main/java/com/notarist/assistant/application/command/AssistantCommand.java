package com.notarist.assistant.application.command;

import com.notarist.assistant.domain.model.AssistantSafetyMode;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.UUID;

public record AssistantCommand(
        UUID queryId,
        UUID sessionId,
        UUID tenantId,
        UUID userId,
        String rawQuery,
        ClassificationLevel maxClassificationLevel,
        JenisDokumen documentTypeFilter,
        AssistantSafetyMode safetyMode,
        int maxResults,
        int contextTokenBudget
) {
    public AssistantCommand {
        if (queryId == null)          queryId = UUID.randomUUID();
        if (sessionId == null)        sessionId = UUID.randomUUID();
        if (tenantId == null)         throw new IllegalArgumentException("tenantId required");
        if (userId == null)           throw new IllegalArgumentException("userId required");
        if (rawQuery == null || rawQuery.isBlank()) throw new IllegalArgumentException("rawQuery required");
        if (maxClassificationLevel == null) maxClassificationLevel = ClassificationLevel.INTERNAL;
        if (safetyMode == null)       safetyMode = AssistantSafetyMode.STRICT;
        if (maxResults <= 0 || maxResults > 20) maxResults = 10;
        // Reserve budget: ~400 system prompt tokens + ~600 citation tokens = 1000 reserved
        if (contextTokenBudget <= 0)  contextTokenBudget = 3072;
    }
}
