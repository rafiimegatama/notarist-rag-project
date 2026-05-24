package com.notarist.document.application.query;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.document.domain.model.DocumentStatus;

import java.util.UUID;

public record ListDocumentsQuery(
        UUID tenantId,
        UUID actorUserId,
        String actorRole,
        ClassificationLevel callerMaxClearance,
        JenisDokumen documentTypeFilter,
        DocumentStatus statusFilter,
        int page,
        int size,
        CorrelationId correlationId
) {
    public ListDocumentsQuery {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
    }
}
