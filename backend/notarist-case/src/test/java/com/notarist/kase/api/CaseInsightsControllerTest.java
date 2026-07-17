package com.notarist.kase.api;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.api.response.ActivityResponse;
import com.notarist.kase.api.response.CaseStatisticsResponse;
import com.notarist.kase.api.rest.CaseExceptionHandler;
import com.notarist.kase.api.rest.CaseInsightsController;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.port.in.CaseAnalyticsUseCase;
import com.notarist.kase.application.port.in.CaseManagementUseCase;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.exception.CaseNotFoundException;
import com.notarist.kase.domain.valueobject.CaseId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CaseInsightsControllerTest {

    private CaseAnalyticsUseCase analytics;
    private CaseManagementUseCase caseManagement;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        analytics = mock(CaseAnalyticsUseCase.class);
        caseManagement = mock(CaseManagementUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CaseInsightsController(analytics, caseManagement, new CallerContextResolver()))
                .setControllerAdvice(new CaseExceptionHandler())
                .build();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "STAFF"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    @Test
    void statisticsReturns200() throws Exception {
        when(analytics.getStatistics(any(CallerContext.class))).thenReturn(new CaseStatisticsResponse(
                Map.of("CASE_CREATED", 6L),
                Map.of("APHT", 2L, "FIDUSIA", 3L),
                Map.of("HIGH", 2L, "NORMAL", 3L),
                List.of(new CaseStatisticsResponse.MonthlyTrendPoint("2026-07", 5))));

        mockMvc.perform(get("/api/v1/cases/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priorityCounts.HIGH").value(2))
                .andExpect(jsonPath("$.data.monthlyTrend[0].month").value("2026-07"));
    }

    @Test
    void activitiesReturns200() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseManagement.getActivities(any(CaseId.class), any(CallerContext.class))).thenReturn(List.of(
                new ActivityResponse(UUID.randomUUID(), "WORKFLOW", "CASE_OPENED", "Case dibuka",
                        UUID.randomUUID(), "STAFF", 0, "2026-07-15T00:00:00Z")));

        mockMvc.perform(get("/api/v1/cases/" + caseId + "/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("WORKFLOW"))
                .andExpect(jsonPath("$.data[0].type").value("CASE_OPENED"));
    }

    @Test
    void activitiesNotFoundMapsTo404() throws Exception {
        when(caseManagement.getActivities(any(CaseId.class), any(CallerContext.class)))
                .thenThrow(new CaseNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/activities"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CASE_NOT_FOUND"));
    }
}
