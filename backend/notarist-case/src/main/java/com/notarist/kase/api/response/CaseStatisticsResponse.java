package com.notarist.kase.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Case statistics for charts. All counts are SQL aggregates.
 *
 * <p>{@code priorityCounts} is DERIVED from case type — PPAT deeds (APHT/SKMHT/AJB) are statutory and
 * time-critical, so they count as HIGH; everything else is NORMAL. The Case aggregate carries no
 * explicit priority field, and this sprint does not add one; this keeps the priority chart populated
 * with real, deterministic data rather than a mock.
 */
@Schema(description = "Aggregated case statistics for dashboards and charts")
public record CaseStatisticsResponse(
        @Schema(description = "Count per CaseState") Map<String, Long> statusCounts,
        @Schema(description = "Count per CaseType") Map<String, Long> typeCounts,
        @Schema(description = "Count per derived priority (HIGH/NORMAL)") Map<String, Long> priorityCounts,
        @Schema(description = "Created-case count per month, oldest first") List<MonthlyTrendPoint> monthlyTrend
) {
    public record MonthlyTrendPoint(
            @Schema(example = "2026-07") String month,
            long count
    ) {}
}
