package com.notarist.web.config;

import com.notarist.core.api.response.ApiError;
import com.notarist.core.api.response.ApiErrorDetail;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.exception.DocumentNotFoundException;
import com.notarist.core.domain.exception.NotaristException;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * Global exception handler — maps domain exceptions to standard ApiResponse error envelopes.
 * requestId is taken from X-Correlation-ID header.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            DocumentNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            UnauthorizedAccessException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            ValidationException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getFieldErrors().stream()
            .map(fe -> ApiErrorDetail.of(fe.field(), fe.issue()))
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of(ex.getErrorCode(), ex.getMessage(), details)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBeanValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> ApiErrorDetail.of(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of("VALIDATION_FIELD_REQUIRED", "Validation failed", details)));
    }

    @ExceptionHandler(NotaristException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotaristException(
            NotaristException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of("SYSTEM_INTERNAL_ERROR", "Internal server error")));
    }

    private String getCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        return (correlationId != null) ? correlationId : UUID.randomUUID().toString();
    }
}
