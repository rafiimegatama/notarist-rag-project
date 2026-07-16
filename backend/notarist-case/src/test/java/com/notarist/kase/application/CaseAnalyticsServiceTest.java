package com.notarist.kase.application;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.kase.api.response.CaseStatisticsResponse;
import com.notarist.kase.api.response.DashboardSummaryResponse;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository.MonthlyCount;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository.WindowCounts;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.application.service.CaseAnalyticsService;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.domain.valueobject.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseAnalyticsServiceTest {

    private CaseAnalyticsRepository repo;
    private CaseAnalyticsService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(CaseAnalyticsRepository.class);
        service = new CaseAnalyticsService(repo);
    }

    private CallerContext caller() {
        return new CallerContext(UUID.randomUUID(), tenantId, Role.STAFF, CorrelationId.generate());
    }

    @Test
    @DisplayName("summary rolls per-state counts into the coarse dashboard buckets")
    void summaryBuckets() {
        Map<CaseState, Long> byState = new LinkedHashMap<>();
        byState.put(CaseState.CASE_CREATED, 2L);
        byState.put(CaseState.UPLOADING, 1L);          // OPEN
        byState.put(CaseState.WAITING_VERIFICATION, 1L); // VERIFICATION
        byState.put(CaseState.GENERATING_DRAFT, 1L);   // DRAFT
        byState.put(CaseState.WAITING_QC, 1L);         // QC
        byState.put(CaseState.FINALIZED, 1L);          // APPROVED
        byState.put(CaseState.DELIVERED, 1L);          // DELIVERED
        byState.put(CaseState.CANCELLED, 1L);          // REJECTED
        when(repo.countByState(tenantId)).thenReturn(byState);
        when(repo.windowCounts(any(), any(), any(), any()))
                .thenReturn(new WindowCounts(9, 1, 3, 5, 90_000.0));  // 90000s = 1d 1h

        DashboardSummaryResponse s = service.getSummary(caller());

        assertThat(s.totalCases()).isEqualTo(9);
        assertThat(s.open()).isEqualTo(3);         // CASE_CREATED(2) + UPLOADING(1)
        assertThat(s.verification()).isEqualTo(1);
        assertThat(s.draft()).isEqualTo(1);
        assertThat(s.qc()).isEqualTo(1);
        assertThat(s.approved()).isEqualTo(1);
        assertThat(s.delivered()).isEqualTo(1);
        assertThat(s.rejected()).isEqualTo(1);
        assertThat(s.casesToday()).isEqualTo(1);
        assertThat(s.casesThisWeek()).isEqualTo(3);
        assertThat(s.casesThisMonth()).isEqualTo(5);
        assertThat(s.averageProcessingSeconds()).isEqualTo(90_000.0);
        assertThat(s.averageProcessingHuman()).isEqualTo("1d 1h");
    }

    @Test
    @DisplayName("summary tolerates an empty tenant with no closed cases")
    void summaryEmpty() {
        when(repo.countByState(tenantId)).thenReturn(Map.of());
        when(repo.windowCounts(any(), any(), any(), any()))
                .thenReturn(new WindowCounts(0, 0, 0, 0, null));

        DashboardSummaryResponse s = service.getSummary(caller());
        assertThat(s.totalCases()).isZero();
        assertThat(s.open()).isZero();
        assertThat(s.averageProcessingSeconds()).isNull();
        assertThat(s.averageProcessingHuman()).isNull();
    }

    @Test
    @DisplayName("statistics derives priority from case type (PPAT = HIGH)")
    void statisticsPriorityDerivation() {
        when(repo.countByState(tenantId)).thenReturn(Map.of(CaseState.CASE_CREATED, 6L));
        Map<CaseType, Long> byType = new LinkedHashMap<>();
        byType.put(CaseType.APHT, 2L);      // HIGH
        byType.put(CaseType.AJB, 1L);       // HIGH
        byType.put(CaseType.FIDUSIA, 3L);   // NORMAL
        when(repo.countByType(tenantId)).thenReturn(byType);
        when(repo.monthlyTrend(any(), any()))
                .thenReturn(List.of(new MonthlyCount(2026, 6, 4), new MonthlyCount(2026, 7, 10)));

        CaseStatisticsResponse stats = service.getStatistics(caller());

        assertThat(stats.priorityCounts()).containsEntry("HIGH", 3L).containsEntry("NORMAL", 3L);
        assertThat(stats.typeCounts()).containsEntry("APHT", 2L).containsEntry("FIDUSIA", 3L);
        assertThat(stats.statusCounts()).containsEntry("CASE_CREATED", 6L);
        assertThat(stats.monthlyTrend()).extracting(CaseStatisticsResponse.MonthlyTrendPoint::month)
                .containsExactly("2026-06", "2026-07");
    }
}
