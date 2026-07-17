package com.notarist.kase.api.response;

import com.notarist.kase.domain.model.TimelineEntry;
import com.notarist.kase.domain.model.TimelineEntryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * An activity-feed item. A projection of an existing {@link TimelineEntry} — NO new table, no
 * duplicated data. {@code category} is a coarse grouping the UI can use for an icon/colour.
 */
@Schema(description = "A case activity, projected from the timeline")
public record ActivityResponse(
        UUID id,
        String category,
        String type,
        String description,
        UUID actorUserId,
        String actorRole,
        int sequence,
        String occurredAt
) {
    public static ActivityResponse from(TimelineEntry e) {
        return new ActivityResponse(
                e.entryId().value(),
                categoryOf(e.type()),
                e.type().name(),
                e.description(),
                e.actor().userId(),
                e.actor().role().name(),
                e.sequence(),
                e.occurredAt() != null ? e.occurredAt().toString() : null);
    }

    private static String categoryOf(TimelineEntryType type) {
        return switch (type) {
            case CASE_OPENED, STATE_CHANGED, ROLLBACK, DRAFT -> "WORKFLOW";
            case DOCUMENT_ATTACHED, BUNDLE_LOCKED -> "DOCUMENT";
            case VERIFICATION, QC, APPROVAL -> "REVIEW";
            case DELIVERY -> "DELIVERY";
            case EXCEPTION -> "EXCEPTION";
            case NOTE -> "NOTE";
        };
    }
}
