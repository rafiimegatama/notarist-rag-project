package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NerCompletedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID jobId,
    UUID documentId,
    String nerObjectKey,
    Map<String, Integer> entitiesExtracted,
    String nerEngine,
    boolean piiRedacted,
    long processingMs
) implements DomainEvent {

    public NerCompletedEvent {
        entitiesExtracted = Map.copyOf(entitiesExtracted != null ? entitiesExtracted : Map.of());
    }

    @Override public String eventType() { return "ner.completed"; }
    @Override public String publishedBy() { return "notarist-ingest"; }
}
