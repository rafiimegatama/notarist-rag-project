package com.notarist.ingest.application.command;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentChecksum;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.UUID;

public record InitiateIngestionCommand(
        UUID tenantId,
        UUID uploadedBy,
        String originalFilename,
        DocumentChecksum checksum,
        JenisDokumen documentType,
        ClassificationLevel classificationLevel,
        CorrelationId correlationId
) {
    public InitiateIngestionCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (uploadedBy == null) throw new IllegalArgumentException("uploadedBy required");
        if (originalFilename == null || originalFilename.isBlank())
            throw new IllegalArgumentException("originalFilename required");
        if (checksum == null) throw new IllegalArgumentException("checksum required");
        if (documentType == null) throw new IllegalArgumentException("documentType required");
        if (classificationLevel == null) throw new IllegalArgumentException("classificationLevel required");
        if (correlationId == null) throw new IllegalArgumentException("correlationId required");
    }
}
