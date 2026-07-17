package com.notarist.verification.api.rest;

import com.notarist.core.api.response.ApiError;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.verification.domain.exception.ChecklistItemNotFoundException;
import com.notarist.verification.domain.exception.IllegalVerificationTransitionException;
import com.notarist.verification.domain.exception.VerificationAuthorityException;
import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
import com.notarist.verification.domain.exception.VerificationNotFoundException;
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
 * Maps Verification-context exceptions to the shared {@link ApiResponse} error envelope. Scoped to the
 * verification REST package and given highest precedence so its specific handlers win over the
 * module-wide {@code GlobalExceptionHandler} (which still handles bean-validation and anything not
 * listed here).
 *
 * <ul>
 *   <li>illegal transition / concurrent edit → 409 Conflict</li>
 *   <li>wrong authority for the requested action → 403 Forbidden</li>
 *   <li>a broken business rule (FAIL w/o reason, VERIFY with a mandatory check unmet) → 422</li>
 *   <li>verification / item not found (or cross-tenant) → 404 (indistinguishable, on purpose)</li>
 *   <li>bad enum / malformed id / bad decision → 400 Bad Request</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.notarist.verification.api.rest")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VerificationExceptionHandler {

    @ExceptionHandler(VerificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationNotFound(
            VerificationNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ChecklistItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleItemNotFound(
            ChecklistItemNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "VERIFICATION_ITEM_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalVerificationTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalTransition(
            IllegalVerificationTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "VERIFICATION_ILLEGAL_TRANSITION", ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentModification(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "VERIFICATION_CONCURRENT_MODIFICATION",
                "The verification was modified concurrently; reload and retry.", request);
    }

    @ExceptionHandler(VerificationAuthorityException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthority(
            VerificationAuthorityException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "VERIFICATION_FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(VerificationInvariantViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvariant(
            VerificationInvariantViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFICATION_INVARIANT_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VERIFICATION_BAD_REQUEST", ex.getMessage(), request);
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
