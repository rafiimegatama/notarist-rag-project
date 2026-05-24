package com.notarist.core.domain.valueobject;

import java.util.UUID;

public record TraceId(String value) {

    public TraceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TraceId must not be null or blank");
        }
    }

    public static TraceId generate() {
        return new TraceId(UUID.randomUUID().toString());
    }

    public static TraceId of(String value) {
        return new TraceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
