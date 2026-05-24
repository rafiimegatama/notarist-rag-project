package com.notarist.ingest.domain.model;

import java.util.UUID;

/**
 * Cross-stage traceable identifier for an ingestion job.
 * Immutable. Flows through queue entries, audit records, and all events.
 * Shares the same UUID as JobId — distinct type makes intent explicit.
 */
public record IngestionId(UUID value) {

    public IngestionId {
        if (value == null) throw new IllegalArgumentException("IngestionId value must not be null");
    }

    public static IngestionId generate() {
        return new IngestionId(UUID.randomUUID());
    }

    public static IngestionId of(UUID value) {
        return new IngestionId(value);
    }

    public static IngestionId of(String uuidStr) {
        return new IngestionId(UUID.fromString(uuidStr));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
