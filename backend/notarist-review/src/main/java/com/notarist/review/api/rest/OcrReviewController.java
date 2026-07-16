package com.notarist.review.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.util.NotaristConstants;
import com.notarist.review.api.request.ChangeReviewStatusRequest;
import com.notarist.review.api.request.ReviewFieldRequest;
import com.notarist.review.api.response.OcrReviewResponse;
import com.notarist.review.api.response.OcrReviewSummaryResponse;
import com.notarist.review.api.support.CallerContextResolver;
import com.notarist.review.api.support.FieldDecisionTranslator;
import com.notarist.review.application.command.ChangeReviewStatusCommand;
import com.notarist.review.application.command.ReviewFieldCommand;
import com.notarist.review.application.port.in.OcrReviewUseCase;
import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.FieldId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * OCR Review REST API — the human review process that follows OCR extraction (used by Modul 1's
 * Upload PDF flow). Document-scoped under {@code /documents/{id}/ocr}.
 *
 * <p>Thin by design: it maps HTTP to a use-case call and back, and does no business logic. Field
 * rules, status legality, authority and tenant isolation all live behind {@link OcrReviewUseCase} —
 * in the aggregate and the application service — never here.
 */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/documents/{documentId}/ocr")
@Tag(name = "OCR Review", description = "Human review of OCR-extracted document fields")
@SecurityRequirement(name = "bearerAuth")
public class OcrReviewController {

    private final OcrReviewUseCase useCase;
    private final CallerContextResolver callerResolver;
    private final FieldDecisionTranslator decisionTranslator;

    public OcrReviewController(OcrReviewUseCase useCase, CallerContextResolver callerResolver,
                               FieldDecisionTranslator decisionTranslator) {
        this.useCase = useCase;
        this.callerResolver = callerResolver;
        this.decisionTranslator = decisionTranslator;
    }

    @GetMapping
    @Operation(summary = "Full OCR review payload: pages, bounding boxes, fields, confidence, authority")
    public ResponseEntity<ApiResponse<OcrReviewResponse>> getReview(
            @PathVariable UUID documentId, HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        OcrReviewResponse response = useCase.getReview(DocumentId.of(documentId), caller);
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    @GetMapping("/summary")
    @Operation(summary = "Review progress: accepted / corrected / rejected / remaining")
    public ResponseEntity<ApiResponse<OcrReviewSummaryResponse>> getSummary(
            @PathVariable UUID documentId, HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        OcrReviewSummaryResponse response = useCase.getSummary(DocumentId.of(documentId), caller);
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    @PutMapping("/fields/{fieldId}")
    @Operation(summary = "Record a reviewer's decision on one field")
    public ResponseEntity<ApiResponse<OcrReviewResponse>> reviewField(
            @PathVariable UUID documentId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody ReviewFieldRequest request,
            HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        FieldDecisionTranslator.Translated t =
                decisionTranslator.translate(request.decision(), request.value(), request.reason());

        OcrReviewResponse response = useCase.reviewField(new ReviewFieldCommand(
                DocumentId.of(documentId),
                FieldId.of(fieldId),
                t.decision(),
                t.correctedValue(),
                t.reason(),
                caller));
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    @PatchMapping("/status")
    @Operation(summary = "Request a review-status transition. Legality is decided by the aggregate.")
    public ResponseEntity<ApiResponse<OcrReviewResponse>> changeStatus(
            @PathVariable UUID documentId,
            @Valid @RequestBody ChangeReviewStatusRequest request,
            HttpServletRequest httpRequest) {

        CallerContext caller = callerResolver.resolve(httpRequest);
        OcrReviewResponse response = useCase.changeStatus(new ChangeReviewStatusCommand(
                DocumentId.of(documentId),
                ReviewStatus.valueOf(request.targetStatus()),   // bad name → IllegalArgumentException → 400
                caller));
        return ResponseEntity.ok(ApiResponse.success(meta(caller), response));
    }

    private ApiMeta meta(CallerContext caller) {
        return ApiMeta.of(caller.correlationId().value());
    }
}
