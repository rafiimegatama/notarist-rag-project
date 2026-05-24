package com.notarist.search.application.query;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.search.domain.model.SearchIntent;

import java.util.UUID;

public record SearchQuery(
        UUID queryId,
        String rawQuery,
        UUID tenantId,
        UUID userId,
        ClassificationLevel maxClassificationLevel,
        JenisDokumen documentTypeFilter,
        SearchIntent intentOverride,
        int maxResults,
        int contextTokenBudget,
        CorrelationId correlationId
) {
    public SearchQuery {
        if (queryId == null) queryId = UUID.randomUUID();
        if (rawQuery == null || rawQuery.isBlank()) throw new IllegalArgumentException("rawQuery required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (maxClassificationLevel == null) maxClassificationLevel = ClassificationLevel.INTERNAL;
        if (maxResults <= 0 || maxResults > 50) maxResults = 10;
        if (contextTokenBudget <= 0) contextTokenBudget = 4096;
        if (correlationId == null) throw new IllegalArgumentException("correlationId required");
    }
}
