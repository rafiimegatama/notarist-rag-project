package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.core.util.NotaristConstants;

import java.time.Instant;
import java.util.UUID;

public record IndexingCompletedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID jobId,
    UUID documentId,
    int vectorsIndexed,
    boolean postgresBm25Updated,
    boolean oracleStatusUpdated,
    long processingDurationMs
) implements DomainEvent {

    @Override public String eventType() { return "indexing.completed"; }
    @Override public String publishedBy() { return "notarist-ingest"; }

    public String qdrantCollection() {
        return NotaristConstants.QDRANT_COLLECTION;
    }
}
