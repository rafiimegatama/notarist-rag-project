package com.notarist.kase.domain.model;

import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.TimelineEntryId;

import java.time.Instant;

/**
 * One immutable line in a case's story. An entity of {@link Timeline} — it has identity, but no life
 * outside its timeline, and it is never modified after it is written.
 *
 * <p>{@code sequence} is dense and ordered; the Timeline's invariant rejects any gap, which is what
 * makes removal detectable rather than silent.
 */
public record TimelineEntry(
        TimelineEntryId entryId,
        TimelineEntryType type,
        String description,
        Actor actor,
        Instant occurredAt,
        int sequence
) {
    public TimelineEntry {
        if (entryId == null) throw new IllegalArgumentException("entryId is required");
        if (type == null)    throw new IllegalArgumentException("type is required");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
    }
}
