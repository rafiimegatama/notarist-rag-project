package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record CaseId(UUID value) {

    public CaseId {
        if (value == null) throw new IllegalArgumentException("CaseId value must not be null");
    }

    public static CaseId generate() {
        return new CaseId(UUID.randomUUID());
    }

    public static CaseId of(UUID value) {
        return new CaseId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
