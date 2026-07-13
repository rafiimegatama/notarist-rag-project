package com.notarist.runtime.ner;

import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.ingest.application.port.out.NerServicePort;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.model.ModelRegistry;
import com.notarist.runtime.timeout.TimeoutCancellationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IndoBERT NER HTTP adapter — the real NerServicePort implementation.
 *
 * Calls the NER sidecar: POST /extract
 * Payload: JSON with the MinIO object key of the OCR-extracted text plus
 * document type and extraction config; the sidecar fetches the text from
 * MinIO directly (same object-key-reference contract as PaddleOcrAdapter)
 * and writes its redacted output back to MinIO.
 *
 * Integration rules (mirrors PaddleOcrAdapter):
 *   - Timeout: 30s per document via TimeoutCancellationOrchestrator
 *   - On failure: marks NER degraded and throws — NerWorker wraps into a
 *     retryable IngestionStageException so pipeline retry/DLQ applies
 *   - piiRedacted comes from the sidecar response and gates chunking:
 *     NerWorker refuses to advance the pipeline when it is false
 */
@Component
public class IndoBertNerAdapter implements NerServicePort {

    private static final Logger log = LoggerFactory.getLogger(IndoBertNerAdapter.class);
    private static final long NER_TIMEOUT_MS = 30_000L;

    private final RestTemplate                    restTemplate;
    private final ModelRegistry                   modelRegistry;
    private final RuntimeMetricsRegistry          metrics;
    private final RuntimeDegradationManager       degradation;
    private final TimeoutCancellationOrchestrator timeout;

    public IndoBertNerAdapter(
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
    public NerResult extractEntities(String ocrObjectKey, JenisDokumen documentType, NerConfig config) {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.NER)) {
            log.error("IndoBertNerAdapter: NER is DEGRADED — cannot extract for {}", ocrObjectKey);
            throw new NerRuntimeException("NER service degraded, objectKey=" + ocrObjectKey, null);
        }

        String endpoint = modelRegistry.getNer().endpointUrl() + "/extract";
        long startMs = System.currentTimeMillis();

        try {
            NerResult result = timeout.submitWithTimeout(
                    "ner-" + ocrObjectKey,
                    () -> callNerService(endpoint, ocrObjectKey, documentType, config),
                    NER_TIMEOUT_MS);

            long durationMs = System.currentTimeMillis() - startMs;
            metrics.recordInferenceLatency(ModelProvider.INDOBERT, durationMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.NER, false, null);

            log.debug("IndoBERT NER: {} entities={} piiRedacted={} durationMs={}",
                    ocrObjectKey, result.entitiesExtracted().size(), result.piiRedacted(), durationMs);
            return result;

        } catch (TimeoutCancellationOrchestrator.TimeoutException e) {
            metrics.recordTimeout(ModelProvider.INDOBERT);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.NER, true, "timeout");
            log.error("IndoBERT NER timeout for {}", ocrObjectKey);
            throw new NerRuntimeException("NER timeout for: " + ocrObjectKey, e);

        } catch (NerRuntimeException e) {
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.NER, true, e.getMessage());
            throw e;
        } catch (Exception e) {
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.NER, true, e.getMessage());
            log.error("IndoBERT NER failed for {}: {}", ocrObjectKey, e.getMessage(), e);
            throw new NerRuntimeException("NER failed for: " + ocrObjectKey, e);
        }
    }

    @SuppressWarnings("unchecked")
    private NerResult callNerService(
            String endpoint, String ocrObjectKey, JenisDokumen documentType, NerConfig config) {
        Map<String, Object> body = Map.of(
                "source_object_key", ocrObjectKey,
                "document_type", documentType.name(),
                "min_entity_confidence", config.minEntityConfidence(),
                "require_pii_redaction", config.requirePiiRedaction(),
                "model_variant", config.modelVariant());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                endpoint, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null) throw new NerRuntimeException("NER service returned null response", null);

        String processedObjectKey = (String) result.get("processed_object_key");
        if (processedObjectKey == null || processedObjectKey.isBlank()) {
            throw new NerRuntimeException(
                    "NER response missing processed_object_key for: " + ocrObjectKey, null);
        }

        Map<String, Integer> entityCounts = new HashMap<>();
        Map<String, Object> rawEntities =
                (Map<String, Object>) result.getOrDefault("entities", Map.of());
        rawEntities.forEach((type, count) -> entityCounts.put(type, ((Number) count).intValue()));

        return new NerResult(
                processedObjectKey,
                entityCounts,
                (String) result.getOrDefault("engine_used", config.modelVariant()),
                Boolean.TRUE.equals(result.get("pii_redacted")),
                ((Number) result.getOrDefault("duration_ms", 0)).longValue());
    }

    public static class NerRuntimeException extends RuntimeException {
        public NerRuntimeException(String message, Throwable cause) { super(message, cause); }
    }
}
