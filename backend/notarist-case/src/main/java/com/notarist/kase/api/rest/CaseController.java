package com.notarist.kase.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.api.response.PageResponse;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.core.util.NotaristConstants;
import com.notarist.kase.api.request.ChangeCaseStatusRequest;
import com.notarist.kase.api.request.CreateCaseRequest;
import com.notarist.kase.api.response.CaseResponse;
import com.notarist.kase.api.response.TimelineResponse;
import com.notarist.kase.application.command.ChangeCaseStatusCommand;
import com.notarist.kase.application.command.OpenCaseCommand;
import com.notarist.kase.application.port.in.CaseManagementUseCase;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.application.query.CaseFilter;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.domain.valueobject.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Case REST API — the first production vertical slice of the Case bounded context.
 *
 * <p>Thin by design: it maps HTTP to a use-case call and back, and does no business logic. Authority,
 * legality of transitions, tenant isolation and invariants all live behind
 * {@link CaseManagementUseCase} — in the aggregate and the application service — never here.
 */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/cases")
@Tag(name = "Cases", description = "Notarial case lifecycle — create, list, inspect, transition")
@SecurityRequirement(name = "bearerAuth")
public class CaseController {

    private final CaseManagementUseCase caseManagement;

    public CaseController(CaseManagementUseCase caseManagement) {
        this.caseManagement = caseManagement;
    }

    @PostMapping
    @Operation(summary = "Open a new case")
    public ResponseEntity<ApiResponse<CaseResponse>> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        CallerContext caller = caller(correlationId);

        CaseResponse response = caseManagement.openCase(new OpenCaseCommand(
                request.caseNumber(),
                CaseType.valueOf(request.caseType()),
                request.assignedNotarisId(),
                caller));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping
    @Operation(summary = "List cases for the caller's tenant, with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<CaseResponse>>> listCases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) UUID assignedNotarisId,
            @RequestParam(required = false) UUID assignedStaff,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        CallerContext caller = caller(correlationId);

        CaseFilter filter = new CaseFilter(
                status != null ? CaseState.valueOf(status) : null,
                caseType != null ? CaseType.valueOf(caseType) : null,
                assignedNotarisId,
                assignedStaff,                       // the staff who opened the case (created_by)
                parseInstant(createdFrom, "createdFrom"),
                parseInstant(createdTo, "createdTo"));

        PageResponse<CaseResponse> response = caseManagement.listCases(
                filter, page, Math.min(size, NotaristConstants.MAX_PAGE_SIZE), caller);

        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "Retrieve a single case by id")
    public ResponseEntity<ApiResponse<CaseResponse>> getCase(
            @PathVariable UUID caseId,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        CallerContext caller = caller(correlationId);

        CaseResponse response = caseManagement.getCase(CaseId.of(caseId), caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @PatchMapping("/{caseId}/status")
    @Operation(summary = "Request a status transition. Legality is decided by the aggregate.")
    public ResponseEntity<ApiResponse<CaseResponse>> changeStatus(
            @PathVariable UUID caseId,
            @Valid @RequestBody ChangeCaseStatusRequest request,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        CallerContext caller = caller(correlationId);

        CaseResponse response = caseManagement.changeStatus(new ChangeCaseStatusCommand(
                CaseId.of(caseId),
                CaseState.valueOf(request.targetState()),
                request.reason(),
                caller));

        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping("/{caseId}/timeline")
    @Operation(summary = "The append-only story of a case")
    public ResponseEntity<ApiResponse<TimelineResponse>> getTimeline(
            @PathVariable UUID caseId,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        CallerContext caller = caller(correlationId);

        TimelineResponse response = caseManagement.getTimeline(CaseId.of(caseId), caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private CallerContext caller(CorrelationId correlationId) {
        VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal in context"));
        // The auth Role names are identical to the Case-context Role names by design (see Role javadoc),
        // so this is a 1:1 map from the JWT's highest role — no new claim, no auth change.
        return new CallerContext(
                principal.userId(),
                principal.tenantId(),
                Role.valueOf(principal.highestRole()),
                correlationId);
    }

    private CorrelationId extractCorrelationId(HttpServletRequest request) {
        String header = request.getHeader(NotaristConstants.HEADER_CORRELATION_ID);
        return (header != null && !header.isBlank()) ? CorrelationId.of(header) : CorrelationId.generate();
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    field + " must be an ISO-8601 instant (e.g. 2026-07-15T00:00:00Z)");
        }
    }
}
