package com.notarist.kase.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.kase.api.response.ActivityResponse;
import com.notarist.kase.api.response.CaseStatisticsResponse;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.port.in.CaseAnalyticsUseCase;
import com.notarist.kase.application.port.in.CaseManagementUseCase;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.valueobject.CaseId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Case-scoped read models that sit alongside {@link CaseController} under {@code /api/v1/cases}.
 * Kept in a separate controller so the core CRUD surface is untouched. Spring routes the literal
 * {@code /statistics} segment ahead of the {@code /{caseId}} pattern, so there is no ambiguity.
 */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/cases")
@Tag(name = "Case Insights", description = "Case statistics and activity feed")
@SecurityRequirement(name = "bearerAuth")
public class CaseInsightsController {

    private final CaseAnalyticsUseCase analytics;
    private final CaseManagementUseCase caseManagement;
    private final CallerContextResolver callerResolver;

    public CaseInsightsController(CaseAnalyticsUseCase analytics,
                                  CaseManagementUseCase caseManagement,
                                  CallerContextResolver callerResolver) {
        this.analytics = analytics;
        this.caseManagement = caseManagement;
        this.callerResolver = callerResolver;
    }

    @GetMapping("/statistics")
    @Operation(summary = "Status / type / priority counts and monthly trend for the tenant")
    public ResponseEntity<ApiResponse<CaseStatisticsResponse>> statistics(HttpServletRequest request) {
        CorrelationId correlationId = callerResolver.correlationId(request);
        CallerContext caller = callerResolver.resolve(request);
        CaseStatisticsResponse response = analytics.getStatistics(caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping("/{caseId}/activities")
    @Operation(summary = "Activity feed for a case (its timeline, projected), newest first")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> activities(
            @PathVariable UUID caseId, HttpServletRequest request) {
        CorrelationId correlationId = callerResolver.correlationId(request);
        CallerContext caller = callerResolver.resolve(request);
        List<ActivityResponse> response = caseManagement.getActivities(CaseId.of(caseId), caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }
}
