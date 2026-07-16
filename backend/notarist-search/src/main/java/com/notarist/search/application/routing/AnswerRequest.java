package com.notarist.search.application.routing;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.UUID;

/**
 * A question plus the caller's security context. This is the only input a strategy receives, so no
 * strategy can widen its own access: tenant and clearance are fixed here, at the router boundary.
 */
public record AnswerRequest(
        String rawQuery,
        UUID tenantId,
        UUID userId,
        ClassificationLevel maxClassificationLevel,
        JenisDokumen documentTypeFilter,
        int maxResults,
        int contextTokenBudget,
        boolean strictMode,
        String traceId,
        CorrelationId correlationId
) {
    public AnswerRequest {
        if (rawQuery == null || rawQuery.isBlank()) throw new IllegalArgumentException("rawQuery required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (userId == null)   throw new IllegalArgumentException("userId required");
        if (maxClassificationLevel == null) maxClassificationLevel = ClassificationLevel.INTERNAL;
        if (maxResults <= 0 || maxResults > 50) maxResults = 10;
        if (contextTokenBudget <= 0) contextTokenBudget = 3072;
        if (correlationId == null) correlationId = CorrelationId.generate();
    }
}
