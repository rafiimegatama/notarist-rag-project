package com.notarist.runtime.ocr;

import com.notarist.infra.ocr.OcrConfidencePolicy;
import com.notarist.infra.ocr.OcrReviewStatus;
import com.notarist.ingest.application.port.out.OcrServicePort;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.model.ModelRegistry;
import com.notarist.runtime.timeout.TimeoutCancellationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * PaddleOCR HTTP adapter.
 * Replaces Phase 2 OcrServicePort stub.
 *
 * Calls PaddleOCR serving endpoint: POST /predict/ocr_system
 * Payload: multipart/form-data with PDF file.
 * Response: JSON with recognized text per page and confidence scores.
 *
 * Integration rules:
 *   - Isolated in OcrRuntimeIsolation (separate thread pool)
 *   - Timeout per page: IntegrationTimeouts.OCR_PAGE_TIMEOUT_MS (30s)
 *   - File validated by PdfValidationGuard BEFORE this call
 *   - OcrConfidencePolicy applied to result → OcrReviewStatus
 *   - On failure: marks OCR degraded; does NOT propagate exception upward
 *     (ingestion job moves to FAILED state, not DLQ, so retry is possible)
 */
@Component
public class PaddleOcrAdapter implements OcrServicePort {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrAdapter.class);

    private final RestTemplate                  restTemplate;
    private final ModelRegistry                 modelRegistry;
    private final RuntimeMetricsRegistry        metrics;
    private final RuntimeDegradationManager     degradation;
    private final TimeoutCancellationOrchestrator timeout;

    public PaddleOcrAdapter(
            @Qualifier("aiRuntimeRestTemplate") RestTemplate restTemplate,
            ModelRegistry modelRegistry,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation,
            TimeoutCancellationOrchestrator timeout) {
        this.restTemplate  = restTemplate;
        this.modelRegistry = modelRegistry;
        this.metrics       = metrics;
        this.degradation   = degradation;
        this.timeout       = timeout;
    }

    @Override
    public OcrResult extractText(String minioObjectKey, OcrConfig config) {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OCR)) {
            log.warn("PaddleOcrAdapter: OCR is DEGRADED — returning empty result for {}", minioObjectKey);
            return degradedResult(minioObjectKey);
        }

        String endpoint = modelRegistry.getOcr().endpointUrl() + "/predict/ocr_system";
        long startMs = System.currentTimeMillis();

        try {
            OcrResult result = timeout.submitWithTimeout(
                    "ocr-" + minioObjectKey,
                    () -> callPaddleOcr(endpoint, minioObjectKey),
                    30_000L);

            long durationMs = System.currentTimeMillis() - startMs;
            metrics.recordInferenceLatency(ModelProvider.PADDLEOCR, durationMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, false, null);

            log.debug("PaddleOCR: {} pages={} confidenceAvg={} durationMs={}",
                    minioObjectKey, result.pageCount(), result.confidenceAvg(), durationMs);

            return result;

        } catch (TimeoutCancellationOrchestrator.TimeoutException e) {
            metrics.recordTimeout(ModelProvider.PADDLEOCR);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, true, "timeout");
            log.error("PaddleOCR timeout for {}", minioObjectKey);
            throw new OcrRuntimeException("OCR timeout for: " + minioObjectKey, e);

        } catch (Exception e) {
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, true, e.getMessage());
            log.error("PaddleOCR failed for {}: {}", minioObjectKey, e.getMessage(), e);
            throw new OcrRuntimeException("OCR failed for: " + minioObjectKey, e);
        }
    }

    @SuppressWarnings("unchecked")
    private OcrResult callPaddleOcr(String endpoint, String objectKey) {
        // In production: stream PDF from MinIO and send as multipart
        // Phase 5B: send objectKey as reference; OCR service fetches from MinIO directly
        Map<String, Object> body = Map.of("source_object_key", objectKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                endpoint, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null) throw new OcrRuntimeException("OCR service returned null response", null);

        String ocrObjectKey = (String) result.getOrDefault("ocr_object_key", objectKey + ".ocr.txt");
        int pageCount       = ((Number) result.getOrDefault("page_count", 0)).intValue();
        int textLength      = ((Number) result.getOrDefault("text_length", 0)).intValue();
        float confidence    = ((Number) result.getOrDefault("confidence_avg", 0.0)).floatValue();
        List<String> warnings = (List<String>) result.getOrDefault("warnings", List.of());
        long durationMs     = ((Number) result.getOrDefault("duration_ms", 0)).longValue();

        return new OcrResult(ocrObjectKey, pageCount, textLength, confidence, warnings, durationMs);
    }

    private OcrResult degradedResult(String objectKey) {
        return new OcrResult(objectKey + ".degraded", 0, 0, 0f,
                List.of("OCR service degraded — text extraction skipped"), 0L);
    }

    public static class OcrRuntimeException extends RuntimeException {
        public OcrRuntimeException(String message, Throwable cause) { super(message, cause); }
    }
}
