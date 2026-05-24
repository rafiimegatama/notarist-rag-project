package com.notarist.core.domain.valueobject;

import java.util.UUID;

public record DocumentId(UUID value) {

    public DocumentId {
        if (value == null) throw new IllegalArgumentException("DocumentId value must not be null");
    }

    public static DocumentId of(UUID value) {
        return new DocumentId(value);
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }

    public static DocumentId from(String uuidString) {
        return new DocumentId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
