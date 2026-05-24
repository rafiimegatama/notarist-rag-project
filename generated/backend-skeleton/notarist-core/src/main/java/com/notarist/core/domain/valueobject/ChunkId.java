package com.notarist.core.domain.valueobject;

import java.util.UUID;

public record ChunkId(UUID value) {

    public ChunkId {
        if (value == null) throw new IllegalArgumentException("ChunkId value must not be null");
    }

    public static ChunkId of(UUID value) {
        return new ChunkId(value);
    }

    public static ChunkId generate() {
        return new ChunkId(UUID.randomUUID());
    }

    public static ChunkId from(String uuidString) {
        return new ChunkId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
