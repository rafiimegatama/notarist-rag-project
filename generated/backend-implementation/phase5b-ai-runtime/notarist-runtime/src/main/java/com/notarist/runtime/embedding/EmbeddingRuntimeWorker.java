package com.notarist.runtime.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.model.ModelRegistry;
import com.notarist.runtime.timeout.TimeoutCancellationOrchestrator;
import com.notarist.infra.qdrant.QdrantVectorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real bge-m3 HTTP adapter for embedding generation.
 *
 * Endpoint: POST /embed
 * Request:  {"texts": ["...", "..."]}
 * Response: {"embeddings": [[float, ...], ...], "dimension": 1024}
 *
 * Validates output dimension == 1024 (IMMUTABLE constraint).
 * Isolated in EmbeddingQueueIsolation (core=4/max=8).
 * Timeout: 15s per batch via TimeoutCancellationOrchestrator.
 * On failure: marks EMBEDDING degraded; throws EmbeddingRuntimeException.
 */
@Component
public class EmbeddingRuntimeWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRuntimeWorker.class);
    private static final long   EMBEDDING_TIMEOUT_MS = 15_000L;

    private final RestTemplate                    restTemplate;
    private final ModelRegistry                   modelRegistry;
    private final RuntimeMetricsRegistry          metrics;
    private final RuntimeDegradationManager       degradation;
    private final TimeoutCancellationOrchestrator timeout;
    private final EmbeddingQueueIsolation         queue;
    private final ObjectMapper                    objectMapper;

    public EmbeddingRuntimeWorker(
            RestTemplate restTemplate,
            ModelRegistry modelRegistry,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation,
            TimeoutCancellationOrchestrator timeout,
            EmbeddingQueueIsolation queue,
            ObjectMapper objectMapper) {
        this.restTemplate  = restTemplate;
        this.modelRegistry = modelRegistry;
        this.metrics       = metrics;
        this.degradation   = degradation;
        this.timeout       = timeout;
        this.queue         = queue;
        this.objectMapper  = objectMapper;
    }

    /**
     * Generates embeddings for a batch of texts.
     *
     * @param texts     input texts to embed (max batch size determined by caller)
     * @param batchId   unique ID for this batch (used as timeout traceId)
     * @return list of float arrays, one per input text, dimension=1024
     */
    public List<float[]> embedBatch(List<String> texts, String batchId) {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.EMBEDDING)) {
            log.warn("EmbeddingRuntimeWorker: EMBEDDING degraded — skipping batchId={}", batchId);
            throw new EmbeddingRuntimeException("Embedding service degraded, batchId=" + batchId, null);
        }

        if (queue.isSaturated()) {
            log.warn("EmbeddingRuntimeWorker: embedding queue saturated — rejecting batchId={}", batchId);
            throw new EmbeddingRuntimeException("Embedding queue saturated, batchId=" + batchId, null);
        }

        long startMs = System.currentTimeMillis();
        try {
            List<float[]> result = queue.submit(() ->
                    timeout.submitWithTimeout(batchId, () -> callEmbeddingService(texts, batchId), EMBEDDING_TIMEOUT_MS)
            ).get();

            long durationMs = System.currentTimeMillis() - startMs;
            metrics.recordInferenceLatency(ModelProvider.BGE_M3, durationMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, false, null);

            log.debug("EmbeddingRuntimeWorker: batchId={} texts={} durationMs={}", batchId, texts.size(), durationMs);
            return result;

        } catch (TimeoutCancellationOrchestrator.TimeoutException e) {
            metrics.recordTimeout(ModelProvider.BGE_M3);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, true, "timeout");
            log.error("EmbeddingRuntimeWorker: timeout batchId={}", batchId);
            throw new EmbeddingRuntimeException("Embedding timeout batchId=" + batchId, e);
        } catch (EmbeddingRuntimeException e) {
            throw e;
        } catch (Exception e) {
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, true, e.getMessage());
            log.error("EmbeddingRuntimeWorker: failed batchId={}: {}", batchId, e.getMessage(), e);
            throw new EmbeddingRuntimeException("Embedding failed batchId=" + batchId, e);
        }
    }

    /**
     * Convenience method for single-text embedding.
     */
    public float[] embed(String text, String traceId) {
        List<float[]> results = embedBatch(List.of(text), traceId + "-single");
        return results.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<float[]> callEmbeddingService(List<String> texts, String batchId) throws Exception {
        String endpoint = modelRegistry.getEmbedding().endpointUrl() + "/embed";

        Map<String, Object> body = Map.of("texts", texts);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                endpoint, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null) throw new EmbeddingRuntimeException("Embedding service returned null", null);

        int dimension = ((Number) result.getOrDefault("dimension", 0)).intValue();
        if (dimension != QdrantVectorPayload.REQUIRED_DIMENSION) {
            throw new EmbeddingRuntimeException(
                    "Embedding dimension mismatch: expected=" + QdrantVectorPayload.REQUIRED_DIMENSION +
                    " actual=" + dimension + " batchId=" + batchId, null);
        }

        List<List<Number>> rawEmbeddings = (List<List<Number>>) result.get("embeddings");
        if (rawEmbeddings == null || rawEmbeddings.size() != texts.size()) {
            throw new EmbeddingRuntimeException(
                    "Embedding count mismatch: expected=" + texts.size() +
                    " actual=" + (rawEmbeddings == null ? 0 : rawEmbeddings.size()), null);
        }

        return rawEmbeddings.stream().map(vec -> {
            float[] arr = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).floatValue();
            return arr;
        }).toList();
    }

    public static class EmbeddingRuntimeException extends RuntimeException {
        public EmbeddingRuntimeException(String message, Throwable cause) { super(message, cause); }
    }
}
