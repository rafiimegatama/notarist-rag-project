package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OcrCompletedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    UUID jobId,
    UUID documentId,
    String ocrObjectKey,
    int pageCount,
    int extractedTextLength,
    float confidenceAvg,
    List<String> ocrWarnings,
    long processingMs
) implements DomainEvent {

    @Override public String eventType() { return "ocr.completed"; }
    @Override public String publishedBy() { return "notarist-ingest"; }
}
