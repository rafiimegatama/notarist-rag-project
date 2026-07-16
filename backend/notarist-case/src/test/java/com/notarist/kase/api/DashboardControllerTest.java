package com.notarist.kase.api;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.api.response.DashboardSummaryResponse;
import com.notarist.kase.api.rest.DashboardController;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.port.in.CaseAnalyticsUseCase;
import com.notarist.kase.application.query.CallerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private CaseAnalyticsUseCase analytics;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        analytics = mock(CaseAnalyticsUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(analytics, new CallerContextResolver()))
                .build();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "STAFF"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    @Test
    void summaryReturns200WithEnvelope() throws Exception {
        when(analytics.getSummary(any(CallerContext.class)))
                .thenReturn(new DashboardSummaryResponse(9, 3, 1, 1, 1, 1, 1, 1, 1, 3, 5, 90_000.0, "1d 1h"));

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalCases").value(9))
                .andExpect(jsonPath("$.data.open").value(3))
                .andExpect(jsonPath("$.data.averageProcessingHuman").value("1d 1h"));
    }
}
