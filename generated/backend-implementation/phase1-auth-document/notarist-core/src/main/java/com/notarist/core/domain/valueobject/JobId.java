package com.notarist.core.domain.valueobject;

import java.util.UUID;

/** Immutable identity for an ingestion pipeline job. */
public record JobId(UUID value) {

    public JobId {
        if (value == null) throw new IllegalArgumentException("JobId value must not be null");
    }

    public static JobId generate() {
        return new JobId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
