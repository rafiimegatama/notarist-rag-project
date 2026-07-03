package com.notarist.core.domain.valueobject;

import java.util.UUID;

/** Immutable identity for a document chunk — persisted in Qdrant and chunk metadata table. */
public record ChunkId(UUID value) {

    public ChunkId {
        if (value == null) throw new IllegalArgumentException("ChunkId value must not be null");
    }

    public static ChunkId generate() {
        return new ChunkId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
