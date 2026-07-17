package com.notarist.verification.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for every event raised by the Verification context. Implements the core {@link DomainEvent}
 * contract — correlation and trace IDs travel on every event. Events are facts in the past tense:
 * they carry identity plus the minimum a consumer needs, never a whole aggregate.
 */
public abstract class VerificationDomainEvent implements DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant timestamp = Instant.now();
    private final CorrelationId correlationId;
    private final TraceId traceId;

    protected VerificationDomainEvent(CorrelationId correlationId, TraceId traceId) {
        this.correlationId = correlationId != null ? correlationId : CorrelationId.generate();
        this.traceId = traceId != null ? traceId : TraceId.generate();
    }

    @Override public UUID eventId()                { return eventId; }
    @Override public Instant timestamp()           { return timestamp; }
    @Override public CorrelationId correlationId() { return correlationId; }
    @Override public TraceId traceId()             { return traceId; }
    @Override public String publishedBy()          { return "notarist-verification"; }
}
