package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.core.util.NotaristConstants;

import java.time.Instant;
import java.util.UUID;

public record EmbeddingCompletedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID jobId,
    UUID documentId,
    int totalVectors,
    long processingMs
) implements DomainEvent {

    @Override public String eventType() { return "embedding.completed"; }
    @Override public String publishedBy() { return "notarist-ingest"; }

    public String embeddingModel() { return com.notarist.core.domain.vector.EmbeddingContract.EMBEDDING_MODEL; }
    public int embeddingDimension() { return NotaristConstants.EMBEDDING_DIMENSION; }
}
