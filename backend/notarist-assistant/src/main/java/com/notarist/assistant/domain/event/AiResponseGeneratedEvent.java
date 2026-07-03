package com.notarist.assistant.domain.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.SessionId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.UUID;

public record AiResponseGeneratedEvent(
    UUID eventId,
    Instant timestamp,
    CorrelationId correlationId,
    TraceId traceId,
    SessionId sessionId,
    UUID userId,
    UUID tenantId,
    String queryHash,
    int citationCount,
    int tokensInput,
    int tokensOutput,
    String modelId,
    float groundingScore,
    boolean hallucinationFlagRaised,
    String streamMode,
    long streamDurationMs,
    long ttftMs
) implements DomainEvent {

    @Override public String eventType() { return "ai.response.generated"; }
    @Override public String publishedBy() { return "notarist-assistant"; }
}
