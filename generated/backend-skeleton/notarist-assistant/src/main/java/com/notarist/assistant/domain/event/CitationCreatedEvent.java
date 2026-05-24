package com.notarist.assistant.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.*;

import java.time.Instant;
import java.util.UUID;

public record CitationCreatedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID citationId,
    SessionId sessionId,
    ChunkId chunkId,
    DocumentId documentId,
    UUID userId,
    UUID tenantId,
    float confidence,
    boolean verified,
    int citationIndex
) implements DomainEvent {

    @Override public String eventType() { return "citation.created"; }
    @Override public String publishedBy() { return "notarist-assistant"; }
}
