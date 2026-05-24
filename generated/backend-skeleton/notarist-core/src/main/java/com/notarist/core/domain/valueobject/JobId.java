package com.notarist.core.domain.valueobject;

import java.util.UUID;

public record JobId(UUID value) {

    public JobId {
        if (value == null) throw new IllegalArgumentException("JobId value must not be null");
    }

    public static JobId of(UUID value) {
        return new JobId(value);
    }

    public static JobId generate() {
        return new JobId(UUID.randomUUID());
    }

    public static JobId from(String uuidString) {
        return new JobId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
