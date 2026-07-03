package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.UUID;

public record ChunkingCompletedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID jobId,
    UUID documentId,
    int totalChunks,
    String chunkStrategy,
    int avgTokensPerChunk,
    int minTokens,
    int maxTokens,
    int overlapPercent,
    String chunkObjectKey,
    long processingMs
) implements DomainEvent {

    @Override public String eventType() { return "chunking.completed"; }
    @Override public String publishedBy() { return "notarist-ingest"; }
}
