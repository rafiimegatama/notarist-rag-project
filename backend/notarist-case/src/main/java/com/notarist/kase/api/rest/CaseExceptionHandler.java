package com.notarist.kase.api.rest;

import com.notarist.core.api.response.ApiError;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.CaseNotFoundException;
import com.notarist.kase.domain.exception.DuplicateCaseNumberException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
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
 * Maps Case-context exceptions to the shared {@link ApiResponse} error envelope. Scoped to the case
 * REST package (so it covers {@link CaseController}, {@link CaseInsightsController} and the dashboard/
 * reminder controllers) and given highest precedence so its specific handlers win over the
 * module-wide {@code GlobalExceptionHandler} — which still handles bean-validation and anything not
 * listed here.
 *
 * <p>The status codes encode the meaning of each domain failure:
 * <ul>
 *   <li>illegal transition / duplicate number / concurrent edit → 409 Conflict</li>
 *   <li>wrong authority for the requested edge → 403 Forbidden</li>
 *   <li>a broken invariant (e.g. rollback with no reason) → 422 Unprocessable Entity</li>
 *   <li>not found / cross-tenant → 404 (indistinguishable, on purpose)</li>
 *   <li>bad enum / malformed id / bad date → 400 Bad Request</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.notarist.kase.api.rest")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CaseExceptionHandler {

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            CaseNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateCaseNumberException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(
            DuplicateCaseNumberException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CASE_NUMBER_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalTransition(
            IllegalTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CASE_ILLEGAL_TRANSITION", ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentModification(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CASE_CONCURRENT_MODIFICATION",
                "The case was modified concurrently; reload and retry.", request);
    }

    @ExceptionHandler(AuthorityException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthority(
            AuthorityException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "CASE_FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvariant(
            InvariantViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "CASE_INVARIANT_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "CASE_BAD_REQUEST", ex.getMessage(), request);
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
