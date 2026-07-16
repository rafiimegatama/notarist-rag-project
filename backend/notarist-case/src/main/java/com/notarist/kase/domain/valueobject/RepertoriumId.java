package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record RepertoriumId(UUID value) {

    public RepertoriumId {
        if (value == null) throw new IllegalArgumentException("RepertoriumId value must not be null");
    }

    public static RepertoriumId generate() {
        return new RepertoriumId(UUID.randomUUID());
    }

    public static RepertoriumId of(UUID value) {
        return new RepertoriumId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
