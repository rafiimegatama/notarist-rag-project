package com.notarist.kase.api.response;

import com.notarist.kase.domain.model.Timeline;

import java.util.List;
import java.util.UUID;

/** The append-only story of a case. */
public record TimelineResponse(
        UUID timelineId,
        UUID caseId,
        String status,
        boolean sealed,
        int entryCount,
        String createdAt,
        List<TimelineEntryResponse> entries
) {
    public static TimelineResponse from(Timeline t) {
        return new TimelineResponse(
                t.timelineId().value(),
                t.caseId().value(),
                t.status().name(),
                t.status().isTerminal(),
                t.entryCount(),
                t.createdAt() != null ? t.createdAt().toString() : null,
                t.entries().stream().map(TimelineEntryResponse::from).toList());
    }
}
