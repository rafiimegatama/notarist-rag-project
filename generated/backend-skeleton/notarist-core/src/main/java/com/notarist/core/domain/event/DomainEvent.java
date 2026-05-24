package com.notarist.core.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.UUID;

/**
 * Base contract for all domain events in NOTARIST RAG platform.
 * All events carry correlation and trace IDs for observability.
 * Event version is frozen per contract in STEP 7.5.
 */
public interface DomainEvent {

    UUID eventId();

    String eventType();

    /** Always "1.0" for v1 events. Increment on breaking change per versioning strategy. */
    default String eventVersion() {
        return "1.0";
    }

    Instant timestamp();

    CorrelationId correlationId();

    TraceId traceId();

    /** Module that published this event — e.g., "notarist-ingest". */
    String publishedBy();
}
