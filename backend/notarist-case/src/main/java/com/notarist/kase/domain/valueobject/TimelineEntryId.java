package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record TimelineEntryId(UUID value) {

    public TimelineEntryId {
        if (value == null) throw new IllegalArgumentException("TimelineEntryId value must not be null");
    }

    public static TimelineEntryId generate() {
        return new TimelineEntryId(UUID.randomUUID());
    }

    public static TimelineEntryId of(UUID value) {
        return new TimelineEntryId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
