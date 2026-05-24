package com.notarist.core.domain.valueobject;

import java.util.UUID;

/** Immutable identity for a legal document entity. */
public record DocumentId(UUID value) {

    public DocumentId {
        if (value == null) throw new IllegalArgumentException("DocumentId value must not be null");
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
