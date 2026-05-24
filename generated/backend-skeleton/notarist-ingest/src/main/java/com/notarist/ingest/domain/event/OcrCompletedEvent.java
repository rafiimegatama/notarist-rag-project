package com.notarist.ingest.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PHASE 6A.2-FIX:
 *   - Renamed ocrWarnings → warnings (aligned with OcrResult.warnings)
 *   - Renamed processingMs → durationMs (aligned with OcrResult.durationMs)
 *   - Added defensive copy compact constructor
 */
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
    List<String> warnings,
    long durationMs
) implements DomainEvent {

    public OcrCompletedEvent {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }

    @Override public String eventType() { return "ocr.completed"; }
    @Override public String publishedBy() { return "notarist-ingest"; }
}
