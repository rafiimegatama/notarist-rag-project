package com.notarist.kase.api.bundle;

import com.notarist.core.api.response.ApiError;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.BundleNotFoundException;
import com.notarist.kase.domain.exception.CaseNotFoundException;
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
 * Maps Bundle-context exceptions to the shared {@link ApiResponse} error envelope. Scoped to the
 * bundle REST package and highest precedence so its specific handlers win over the module-wide
 * {@code GlobalExceptionHandler}, which still handles bean-validation and anything not listed here.
 */
@RestControllerAdvice(basePackages = "com.notarist.kase.api.bundle")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BundleExceptionHandler {

    @ExceptionHandler({BundleNotFoundException.class, CaseNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "BUNDLE_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalTransition(
            IllegalTransitionException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "BUNDLE_ILLEGAL_TRANSITION", ex.getMessage(), req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrent(
            OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "BUNDLE_CONCURRENT_MODIFICATION",
                "The bundle was modified concurrently; reload and retry.", req);
    }

    @ExceptionHandler(AuthorityException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthority(AuthorityException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "BUNDLE_FORBIDDEN", ex.getMessage(), req);
    }

    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvariant(
            InvariantViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "BUNDLE_INVARIANT_VIOLATION", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BUNDLE_BAD_REQUEST", ex.getMessage(), req);
    }

    private ResponseEntity<ApiResponse<Void>> build(
            HttpStatus status, String code, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ApiMeta.of(correlationId(req)), ApiError.of(code, message)));
    }

    private String correlationId(HttpServletRequest req) {
        String header = req.getHeader("X-Correlation-ID");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
