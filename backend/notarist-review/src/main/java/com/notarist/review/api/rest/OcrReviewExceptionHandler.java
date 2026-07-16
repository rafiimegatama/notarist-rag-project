package com.notarist.review.api.rest;

import com.notarist.core.api.response.ApiError;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.review.domain.exception.FieldNotFoundException;
import com.notarist.review.domain.exception.IllegalReviewTransitionException;
import com.notarist.review.domain.exception.ReviewAuthorityException;
import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import com.notarist.review.domain.exception.ReviewNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Maps Review-context exceptions to the shared {@link ApiResponse} error envelope. Scoped to the
 * review REST package and given highest precedence so its specific handlers win over the module-wide
 * {@code GlobalExceptionHandler} (which still handles bean-validation and anything not listed here).
 *
 * <ul>
 *   <li>illegal transition / concurrent edit → 409 Conflict</li>
 *   <li>wrong authority for the requested action → 403 Forbidden</li>
 *   <li>a broken business rule (reject w/o reason, complete with undecided fields) → 422</li>
 *   <li>review / field not found (or cross-tenant) → 404 (indistinguishable, on purpose)</li>
 *   <li>bad enum / malformed id / bad decision → 400 Bad Request</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.notarist.review.api.rest")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OcrReviewExceptionHandler {

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotFound(
            ReviewNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "OCR_REVIEW_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(FieldNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldNotFound(
            FieldNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "OCR_FIELD_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalReviewTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalTransition(
            IllegalReviewTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "OCR_REVIEW_ILLEGAL_TRANSITION", ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentModification(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "OCR_REVIEW_CONCURRENT_MODIFICATION",
                "The review was modified concurrently; reload and retry.", request);
    }

    @ExceptionHandler(ReviewAuthorityException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthority(
            ReviewAuthorityException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "OCR_REVIEW_FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(ReviewInvariantViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvariant(
            ReviewInvariantViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "OCR_REVIEW_INVARIANT_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "OCR_REVIEW_BAD_REQUEST", ex.getMessage(), request);
    }

    private ResponseEntity<ApiResponse<Void>> build(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ApiMeta.of(correlationId(request)), ApiError.of(code, message)));
    }

    private String correlationId(HttpServletRequest request) {
        String header = request.getHeader("X-Correlation-ID");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
