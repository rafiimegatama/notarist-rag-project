package com.notarist.kase.application.service;

import com.notarist.kase.api.response.CaseStatisticsResponse;
import com.notarist.kase.api.response.DashboardSummaryResponse;
import com.notarist.kase.application.port.in.CaseAnalyticsUseCase;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository.MonthlyCount;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository.WindowCounts;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard summary and case statistics — factual SQL aggregates only. The service does no counting
 * itself: it asks the analytics repository for grouped counts (one query each) and only re-shapes them
 * into the DTOs the frontend expects.
 */
@Service
public class CaseAnalyticsService implements CaseAnalyticsUseCase {

    /** Coarse dashboard buckets, rolled up from the fine-grained {@link CaseState}. */
    private enum Bucket { OPEN, VERIFICATION, DRAFT, QC, APPROVED, DELIVERED, REJECTED }

    /** Derived priority. PPAT deeds are statutory/time-critical; the rest are normal. */
    private enum Priority { HIGH, NORMAL }

    private final CaseAnalyticsRepository analytics;

    public CaseAnalyticsService(CaseAnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    @Override
    public DashboardSummaryResponse getSummary(CallerContext caller) {
        UUID tenantId = caller.tenantId();
        Map<CaseState, Long> byState = analytics.countByState(tenantId);

        Map<Bucket, Long> buckets = new EnumMap<>(Bucket.class);
        for (Bucket b : Bucket.values()) buckets.put(b, 0L);
        byState.forEach((state, count) -> buckets.merge(bucketOf(state), count, Long::sum));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        WindowCounts w = analytics.windowCounts(tenantId, dayStart, weekStart, monthStart);

        return new DashboardSummaryResponse(
                w.total(),
                buckets.get(Bucket.OPEN),
                buckets.get(Bucket.VERIFICATION),
                buckets.get(Bucket.DRAFT),
                buckets.get(Bucket.QC),
                buckets.get(Bucket.APPROVED),
                buckets.get(Bucket.DELIVERED),
                buckets.get(Bucket.REJECTED),
                w.today(),
                w.thisWeek(),
                w.thisMonth(),
                w.averageProcessingSeconds(),
                humaniseSeconds(w.averageProcessingSeconds()));
    }

    @Override
    public CaseStatisticsResponse getStatistics(CallerContext caller) {
        UUID tenantId = caller.tenantId();

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        analytics.countByState(tenantId).forEach((s, c) -> statusCounts.put(s.name(), c));

        Map<CaseType, Long> byType = analytics.countByType(tenantId);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        byType.forEach((t, c) -> typeCounts.put(t.name(), c));

        Map<String, Long> priorityCounts = new LinkedHashMap<>();
        priorityCounts.put(Priority.HIGH.name(), 0L);
        priorityCounts.put(Priority.NORMAL.name(), 0L);
        byType.forEach((t, c) -> priorityCounts.merge(priorityOf(t).name(), c, Long::sum));

        // Last 12 months, oldest first.
        Instant since = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .minusMonths(11).atStartOfDay(ZoneOffset.UTC).toInstant();
        var trend = analytics.monthlyTrend(tenantId, since).stream()
                .map(m -> new CaseStatisticsResponse.MonthlyTrendPoint(monthLabel(m), m.count()))
                .toList();

        return new CaseStatisticsResponse(statusCounts, typeCounts, priorityCounts, trend);
    }

    // ---- mappings ------------------------------------------------------------------------------

    private static Bucket bucketOf(CaseState state) {
        return switch (state) {
            case CASE_CREATED, UPLOADING, OCR_RUNNING, OCR_FAILED, FIELD_EXTRACTION -> Bucket.OPEN;
            case WAITING_VERIFICATION, VERIFIED -> Bucket.VERIFICATION;
            case GENERATING_DRAFT, DRAFT_FAILED -> Bucket.DRAFT;
            case WAITING_QC, QC_FAILED, QC_APPROVED -> Bucket.QC;
            case WAITING_NOTARY_APPROVAL, FINALIZED -> Bucket.APPROVED;
            case DELIVERED, ARCHIVED -> Bucket.DELIVERED;
            case CANCELLED -> Bucket.REJECTED;
        };
    }

    private static Priority priorityOf(CaseType type) {
        return type.requiresPpat() ? Priority.HIGH : Priority.NORMAL;
    }

    private static String monthLabel(MonthlyCount m) {
        return String.format("%04d-%02d", m.year(), m.month());
    }

    private static String humaniseSeconds(Double seconds) {
        if (seconds == null) return null;
        long total = Math.round(seconds);
        long days = total / 86_400;
        long hours = (total % 86_400) / 3_600;
        long minutes = (total % 3_600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
