package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record RepertoriumEntryId(UUID value) {

    public RepertoriumEntryId {
        if (value == null) throw new IllegalArgumentException("RepertoriumEntryId value must not be null");
    }

    public static RepertoriumEntryId generate() {
        return new RepertoriumEntryId(UUID.randomUUID());
    }

    public static RepertoriumEntryId of(UUID value) {
        return new RepertoriumEntryId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
