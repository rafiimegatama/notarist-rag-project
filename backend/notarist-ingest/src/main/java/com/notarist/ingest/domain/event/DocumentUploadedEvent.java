package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.*;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID jobId,
    UUID documentId,
    UUID tenantId,
    UUID uploadedBy,
    String objectKey,
    String originalFilename,
    JenisDokumen documentType,
    JenisAkta jenisAkta,
    String mimeType,
    String checksumSha256,
    Long fileSizeBytes,
    ClassificationLevel classificationLevel
) implements DomainEvent {

    @Override public String eventType() { return "document.uploaded"; }
    @Override public String publishedBy() { return "notarist-ingest"; }
}
