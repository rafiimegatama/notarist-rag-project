package com.notarist.web.config;

import com.notarist.core.api.response.ApiError;
import com.notarist.core.api.response.ApiErrorDetail;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.exception.DocumentNotFoundException;
import com.notarist.core.domain.exception.NotaristException;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.exception.ValidationException;
import com.notarist.ingest.domain.exception.IngestionStageException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Global exception handler — maps domain exceptions to standard ApiResponse error envelopes.
 * requestId is taken from X-Correlation-ID header.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(IngestionStageException.class)
    public ResponseEntity<ApiResponse<Void>> handleIngestionStage(
            IngestionStageException ex, HttpServletRequest request) {
        HttpStatus status = ex.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(NotaristException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotaristException(
            NotaristException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(
                ApiMeta.of(getCorrelationId(request)),
                ApiError.of(ex.getErrorCode(), ex.getMessage())));
    }

    /**
     * A body the parser cannot read is a 400, not a 500.
     *
     * <p>Nothing mapped {@link HttpMessageNotReadableException}, so malformed JSON, a wrong field
     * type, or an unknown property fell to the catch-all and came back as "Internal server error".
     * The caller sent the bad request and was told the server had failed — and every such request
     * counted as a 5xx against the error-rate alert in {@code modules/monitoring}.
     *
     * <p>The parser's own message is surfaced, not swallowed: it names the offending field, which is
     * exactly what the caller needs to fix the request, and it describes the caller's own payload —
     * not server internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Malformed request body [correlationId={}] {} {}: {}",
                correlationId, request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(
                ApiMeta.of(correlationId),
                ApiError.of("VALIDATION_MALFORMED_BODY", rootMessage(ex))));
    }

    /** Jackson nests the useful part; the outermost message is mostly framework noise. */
    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return (message == null || message.isBlank()) ? "Request body could not be parsed" : message;
    }

    /**
     * An unmatched URL is a 404, not a server error.
     *
     * <p>Spring Boot 3.2 raises {@link NoResourceFoundException} when no handler matches and the
     * request falls through to the static-resource handler. This class does not extend
     * {@code ResponseEntityExceptionHandler}, so nothing mapped it and the catch-all below claimed
     * it — turning every mistyped URL into a 500 with a stack trace. That is wrong twice over: the
     * caller is told the server broke when the caller was at fault, and 5xx dashboards and alerts
     * light up for what is really a client typo.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("No handler [correlationId={}] {} {}",
                correlationId, request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(
                ApiMeta.of(correlationId),
                ApiError.of("RESOURCE_NOT_FOUND", "No endpoint matches " + request.getRequestURI())));
    }

    /**
     * Catch-all. The response deliberately says nothing but "Internal server error" — leaking a
     * stack trace or a SQL fragment to a caller is not something a legal-document system should do.
     *
     * <p>But the exception has to be logged, and it was not: this handler had no logger, so every
     * unexpected 500 vanished. The correlation ID went out in the response body and matched nothing
     * on the server, which is worse than useless — it looks like a trace ID that can be followed.
     * The only 500s that left any evidence were the ones Hibernate happened to log on its own.
     *
     * <p>Logged at ERROR with the correlation ID and the request line, so the ID a caller reports
     * actually finds the trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(
            Exception ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Unhandled exception [correlationId={}] {} {}",
                correlationId, request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                ApiMeta.of(correlationId),
                ApiError.of("SYSTEM_INTERNAL_ERROR", "Internal server error")));
    }

    private String getCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        return (correlationId != null) ? correlationId : UUID.randomUUID().toString();
    }
}
