package com.notarist.kase.api.bundle;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.kase.api.request.ChangeBundleStatusRequest;
import com.notarist.kase.api.request.CreateBundleRequest;
import com.notarist.kase.api.response.BundleResponse;
import com.notarist.kase.api.response.BundleTimelineResponse;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.command.ChangeBundleStatusCommand;
import com.notarist.kase.application.command.OpenBundleCommand;
import com.notarist.kase.application.port.in.BundleManagementUseCase;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.BundleType;
import com.notarist.kase.domain.valueobject.CaseId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Bundle REST API. Thin: maps HTTP to a use-case call and back. Authority, transition legality,
 * tenant isolation and the delivery guard all live behind {@link BundleManagementUseCase}.
 *
 * <p>Deliberately in {@code com.notarist.kase.api.bundle} (not {@code api.rest}) so it is handled by
 * {@link BundleExceptionHandler} rather than the case module's REST advice.
 */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH)
@Tag(name = "Bundles", description = "Bundle lifecycle — the working document collections of a case")
@SecurityRequirement(name = "bearerAuth")
public class BundleController {

    private final BundleManagementUseCase bundles;
    private final CallerContextResolver callerResolver;

    public BundleController(BundleManagementUseCase bundles, CallerContextResolver callerResolver) {
        this.bundles = bundles;
        this.callerResolver = callerResolver;
    }

    @PostMapping("/cases/{caseId}/bundles")
    @Operation(summary = "Open a new bundle on a case")
    public ResponseEntity<ApiResponse<BundleResponse>> createBundle(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateBundleRequest request,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = callerResolver.correlationId(httpRequest);
        CallerContext caller = callerResolver.resolve(httpRequest);

        BundleResponse response = bundles.openBundle(new OpenBundleCommand(
                CaseId.of(caseId),
                BundleType.valueOf(request.bundleType()),
                request.expectedDocumentCount(),
                caller));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping("/cases/{caseId}/bundles")
    @Operation(summary = "List the bundles of a case")
    public ResponseEntity<ApiResponse<List<BundleResponse>>> listBundles(
            @PathVariable UUID caseId, HttpServletRequest httpRequest) {

        CorrelationId correlationId = callerResolver.correlationId(httpRequest);
        CallerContext caller = callerResolver.resolve(httpRequest);

        List<BundleResponse> response = bundles.listBundles(CaseId.of(caseId), caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping("/bundles/{bundleId}")
    @Operation(summary = "Retrieve a single bundle")
    public ResponseEntity<ApiResponse<BundleResponse>> getBundle(
            @PathVariable UUID bundleId, HttpServletRequest httpRequest) {

        CorrelationId correlationId = callerResolver.correlationId(httpRequest);
        CallerContext caller = callerResolver.resolve(httpRequest);

        BundleResponse response = bundles.getBundle(BundleId.of(bundleId), caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @PatchMapping("/bundles/{bundleId}/status")
    @Operation(summary = "Request a bundle status transition. Legality is decided by the aggregate.")
    public ResponseEntity<ApiResponse<BundleResponse>> changeStatus(
            @PathVariable UUID bundleId,
            @Valid @RequestBody ChangeBundleStatusRequest request,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = callerResolver.correlationId(httpRequest);
        CallerContext caller = callerResolver.resolve(httpRequest);

        BundleResponse response = bundles.changeStatus(new ChangeBundleStatusCommand(
                BundleId.of(bundleId),
                BundleWorkflowStatus.valueOf(request.targetStatus()),
                caller));

        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping("/bundles/{bundleId}/timeline")
    @Operation(summary = "The append-only story of a bundle")
    public ResponseEntity<ApiResponse<BundleTimelineResponse>> getTimeline(
            @PathVariable UUID bundleId, HttpServletRequest httpRequest) {

        CorrelationId correlationId = callerResolver.correlationId(httpRequest);
        CallerContext caller = callerResolver.resolve(httpRequest);

        BundleTimelineResponse response = bundles.getTimeline(BundleId.of(bundleId), caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }
}
