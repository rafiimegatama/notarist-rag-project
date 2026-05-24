package com.notarist.ingest.application.command;

import com.notarist.core.domain.valueobject.*;

import java.util.UUID;

public record InitiateIngestionCommand(
    String originalFilename,
    JenisDokumen documentType,
    JenisAkta jenisAkta,
    String mimeType,
    long fileSizeBytes,
    ClassificationLevel classificationLevel,
    DocumentChecksum checksum,
    UUID uploadedBy,
    UUID tenantId,
    CorrelationId correlationId,
    TraceId traceId
) {}
