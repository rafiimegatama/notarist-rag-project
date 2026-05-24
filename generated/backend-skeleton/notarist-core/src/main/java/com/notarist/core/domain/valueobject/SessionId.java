package com.notarist.core.domain.valueobject;

import java.util.UUID;

public record SessionId(UUID value) {

    public SessionId {
        if (value == null) throw new IllegalArgumentException("SessionId value must not be null");
    }

    public static SessionId of(UUID value) {
        return new SessionId(value);
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId from(String uuidString) {
        return new SessionId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
