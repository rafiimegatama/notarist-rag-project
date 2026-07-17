package com.notarist.kase.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Factual dashboard header — pure SQL aggregates, no inference. The coarse buckets (open, verification,
 * …) are convenience roll-ups of the raw per-state counts, which the statistics endpoint exposes in
 * full.
 */
@Schema(description = "Factual case counts for the dashboard header")
public record DashboardSummaryResponse(
        long totalCases,
        long open,
        long verification,
        long draft,
        long qc,
        long approved,
        long delivered,
        long rejected,
        long casesToday,
        long casesThisWeek,
        long casesThisMonth,
        @Schema(description = "Mean closed-case duration in seconds; null when nothing is closed yet")
        Double averageProcessingSeconds,
        @Schema(description = "Same value, humanised, e.g. \"3d 4h\"; null when nothing is closed yet")
        String averageProcessingHuman
) {}
