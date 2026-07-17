package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only, tenant-scoped analytics over the {@code notarial_case} table. Every method is a single
 * aggregate query — counts are computed by the database with GROUP BY / FILTER, never by loading
 * cases and counting in Java, so there is no N+1 and no entity hydration.
 *
 * <p>Separate from {@link CaseRepository} on purpose: that port loads and saves aggregates; this one
 * only summarises. Mixing the two would tempt a caller to load a page of cases to "count" them.
 */
public interface CaseAnalyticsRepository {

    /** Case count per {@link CaseState}. States with zero cases are simply absent from the map. */
    Map<CaseState, Long> countByState(UUID tenantId);

    /** Case count per {@link CaseType}. */
    Map<CaseType, Long> countByType(UUID tenantId);

    /** Total + rolling-window counts + average processing time, in ONE query. */
    WindowCounts windowCounts(UUID tenantId, Instant dayStart, Instant weekStart, Instant monthStart);

    /** Month-by-month created-case counts from {@code since} (inclusive), oldest first. */
    List<MonthlyCount> monthlyTrend(UUID tenantId, Instant since);

    /** The rows the reminder engine needs, filtered in SQL to just the cases that can carry a reminder. */
    List<ReminderCandidate> reminderCandidates(UUID tenantId);

    /** Totals for the dashboard header. {@code averageProcessingSeconds} is null when nothing is closed yet. */
    record WindowCounts(long total, long today, long thisWeek, long thisMonth,
                        Double averageProcessingSeconds) {}

    record MonthlyCount(int year, int month, long count) {}

    record ReminderCandidate(UUID caseId, String caseNumber, CaseType caseType,
                             CaseState state, Instant createdAt) {}
}
