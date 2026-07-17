package com.notarist.kase.api.response;

import com.notarist.kase.domain.model.TimelineEntry;

import java.util.UUID;

/** One line in a case's story. */
public record TimelineEntryResponse(
        UUID entryId,
        String type,
        String description,
        UUID actorUserId,
        String actorRole,
        int sequence,
        String occurredAt
) {
    public static TimelineEntryResponse from(TimelineEntry e) {
        return new TimelineEntryResponse(
                e.entryId().value(),
                e.type().name(),
                e.description(),
                e.actor().userId(),
                e.actor().role().name(),
                e.sequence(),
                e.occurredAt() != null ? e.occurredAt().toString() : null);
    }
}
