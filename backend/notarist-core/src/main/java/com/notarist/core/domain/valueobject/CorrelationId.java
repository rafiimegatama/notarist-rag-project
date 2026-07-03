package com.notarist.core.domain.valueobject;

import java.util.UUID;

/** Cross-service correlation identifier — propagated in events, audit records, and MDC. */
public record CorrelationId(String value) {

    public CorrelationId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CorrelationId value must not be blank");
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    public static CorrelationId of(String value) {
        return new CorrelationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
