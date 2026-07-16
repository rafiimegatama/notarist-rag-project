package com.notarist.kase.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.kase.api.response.DashboardSummaryResponse;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.port.in.CaseAnalyticsUseCase;
import com.notarist.kase.application.query.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Factual dashboard header — SQL aggregates only, no LLM. */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/dashboard")
@Tag(name = "Dashboard", description = "Factual case metrics for the office dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final CaseAnalyticsUseCase analytics;
    private final CallerContextResolver callerResolver;

    public DashboardController(CaseAnalyticsUseCase analytics, CallerContextResolver callerResolver) {
        this.analytics = analytics;
        this.callerResolver = callerResolver;
    }

    @GetMapping("/summary")
    @Operation(summary = "Case counts and processing time for the caller's tenant")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(HttpServletRequest request) {
        CorrelationId correlationId = callerResolver.correlationId(request);
        CallerContext caller = callerResolver.resolve(request);
        DashboardSummaryResponse response = analytics.getSummary(caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }
}
