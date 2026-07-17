package com.notarist.kase.api.response;

import com.notarist.kase.domain.model.BundleTimeline;

import java.util.List;
import java.util.UUID;

/** The append-only story of a bundle. Reuses {@link TimelineEntryResponse} for its entries. */
public record BundleTimelineResponse(
        UUID timelineId,
        UUID bundleId,
        String status,
        boolean sealed,
        int entryCount,
        String createdAt,
        List<TimelineEntryResponse> entries
) {
    public static BundleTimelineResponse from(BundleTimeline t) {
        return new BundleTimelineResponse(
                t.timelineId(),
                t.bundleId().value(),
                t.status().name(),
                t.status().isTerminal(),
                t.entryCount(),
                t.createdAt() != null ? t.createdAt().toString() : null,
                t.entries().stream().map(TimelineEntryResponse::from).toList());
    }
}
