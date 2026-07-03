package com.notarist.document.application.query;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;

import java.util.UUID;

public record GetDocumentQuery(
        DocumentId documentId,
        UUID actorUserId,
        String actorRole,
        UUID tenantId,
        CorrelationId correlationId
) {}
