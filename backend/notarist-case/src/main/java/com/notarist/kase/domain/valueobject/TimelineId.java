package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record TimelineId(UUID value) {

    public TimelineId {
        if (value == null) throw new IllegalArgumentException("TimelineId value must not be null");
    }

    public static TimelineId generate() {
        return new TimelineId(UUID.randomUUID());
    }

    public static TimelineId of(UUID value) {
        return new TimelineId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
