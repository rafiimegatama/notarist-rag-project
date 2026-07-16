package com.notarist.verification.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.util.NotaristConstants;
import com.notarist.verification.api.request.ChangeVerificationStatusRequest;
import com.notarist.verification.api.request.UpdateChecklistItemRequest;
import com.notarist.verification.api.response.VerificationResponse;
import com.notarist.verification.api.response.VerificationSummaryResponse;
import com.notarist.verification.api.support.CallerContextResolver;
import com.notarist.verification.api.support.DecisionTranslator;
import com.notarist.verification.application.command.ChangeVerificationStatusCommand;
import com.notarist.verification.application.command.UpdateChecklistItemCommand;
import com.notarist.verification.application.port.in.VerificationUseCase;
import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.ItemId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Verification REST API — the human verification stage that follows OCR Review and precedes bundle
 * approval. Bundle-scoped under {@code /bundles/{id}/verification}.
 *
 * <p>Thin by design: it maps HTTP to a use-case call and back, and does no business logic. The
 * completion rule, transition legality, authority and tenant isolation all live behind
 * {@link VerificationUseCase} — in the aggregate and the application service — never here.
 */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/bundles/{bundleId}/verification")
@Tag(name = "Verification", description = "Human verification checklist for a bundle")
@SecurityRequirement(name = "bearerAuth")
public class VerificationController {

    private final VerificationUseCase useCase;
    private final CallerContextResolver callerResolver;
    private final DecisionTranslator decisionTranslator;

    public VerificationController(VerificationUseCase useCase, CallerContextResolver callerResolver,
                                  DecisionTranslator decisionTranslator) {
        this.useCase = useCase;
        this.callerResolver = callerResolver;
        this.decisionTranslator = decisionTranslator;
    }

    @GetMapping
    @Operation(summary = "Full verification payload: status, progress, checklist, categories, summary")
    public ResponseEntity<ApiResponse<VerificationResponse>> getVerification(
            @PathVariable UUID bundleId, HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        VerificationResponse response = useCase.getVerification(BundleId.of(bundleId), caller);
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    @GetMapping("/summary")
    @Operation(summary = "Verification progress and whether it can be completed")
    public ResponseEntity<ApiResponse<VerificationSummaryResponse>> getSummary(
            @PathVariable UUID bundleId, HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        VerificationSummaryResponse response = useCase.getSummary(BundleId.of(bundleId), caller);
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    @PostMapping("/checklist/{itemId}")
    @Operation(summary = "Record a decision on one checklist item")
    public ResponseEntity<ApiResponse<VerificationResponse>> updateChecklistItem(
            @PathVariable UUID bundleId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateChecklistItemRequest request,
            HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        DecisionTranslator.Translated t = decisionTranslator.translate(request.decision(), request.comment());

        VerificationResponse response = useCase.updateChecklistItem(new UpdateChecklistItemCommand(
                BundleId.of(bundleId),
                ItemId.of(itemId),
                t.decision(),
                t.comment(),
                caller));
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    @PatchMapping("/status")
    @Operation(summary = "Request a verification-status transition. Legality is decided by the aggregate.")
    public ResponseEntity<ApiResponse<VerificationResponse>> changeStatus(
            @PathVariable UUID bundleId,
            @Valid @RequestBody ChangeVerificationStatusRequest request,
            HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        VerificationResponse response = useCase.changeStatus(new ChangeVerificationStatusCommand(
                BundleId.of(bundleId),
                VerificationStatus.valueOf(request.targetStatus()),   // bad name → IllegalArgumentException → 400
                caller));
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    private ApiMeta meta(CallerContext caller) {
        return ApiMeta.of(caller.correlationId().value());
    }
}
