package com.notarist.kase.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for every event raised by the Case context. Implements the existing core {@link DomainEvent}
 * contract — correlation and trace IDs are carried on every event, as they already are in ingest.
 *
 * <p>Events are facts, in the past tense, and carry identity plus the minimum a consumer needs to
 * react. They never carry whole aggregates: a fat event goes stale in flight, and a consumer that
 * needs more can load it.
 */
public abstract class CaseDomainEvent implements DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant timestamp = Instant.now();
    private final CorrelationId correlationId;
    private final TraceId traceId;

    protected CaseDomainEvent(CorrelationId correlationId, TraceId traceId) {
        this.correlationId = correlationId != null ? correlationId : CorrelationId.generate();
        this.traceId = traceId != null ? traceId : TraceId.generate();
    }

    @Override public UUID eventId()                 { return eventId; }
    @Override public Instant timestamp()            { return timestamp; }
    @Override public CorrelationId correlationId()  { return correlationId; }
    @Override public TraceId traceId()              { return traceId; }
    @Override public String publishedBy()           { return "notarist-case"; }
}
