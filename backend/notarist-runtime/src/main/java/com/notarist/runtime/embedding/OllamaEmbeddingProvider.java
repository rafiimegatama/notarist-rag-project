package com.notarist.runtime.embedding;

import com.notarist.infra.qdrant.QdrantVectorPayload;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.provider.EmbeddingProvider;
import com.notarist.runtime.provider.ProviderCapabilities;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import com.notarist.runtime.timeout.TimeoutCancellationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * {@code ollama} embedding provider — generates dense vectors from the local Ollama server's
 * embedding endpoint (POST {@code /api/embed}, batch {@code input}). Select with
 * {@code EMBED_PROVIDER=ollama}; this is the default for the local RTX 5060 Ti target where a single
 * Ollama process serves both chat and {@code bge-m3} embeddings.
 *
 * <p>Shares {@link EmbeddingQueueIsolation}, {@link TimeoutCancellationOrchestrator} and the
 * {@code EMBEDDING} degradation channel with the sidecar provider, so backpressure, timeout and
 * health semantics are identical regardless of which embedding backend is active. Output dimension
 * is validated against {@link QdrantVectorPayload#REQUIRED_DIMENSION} — a mismatch is fatal.
 */
@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);
    private static final String PROVIDER_ID          = "ollama";
    private static final String EMBED_PATH           = "/api/embed";
    private static final long   EMBEDDING_TIMEOUT_MS = 15_000L;

    private final RestTemplate                    restTemplate;
    private final RuntimeMetricsRegistry          metrics;
    private final RuntimeDegradationManager       degradation;
    private final TimeoutCancellationOrchestrator timeout;
    private final EmbeddingQueueIsolation         queue;
    private final String                          baseUrl;
    private final String                          model;

    public OllamaEmbeddingProvider(
            @Qualifier("aiRuntimeRestTemplate") RestTemplate restTemplate,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation,
            TimeoutCancellationOrchestrator timeout,
            EmbeddingQueueIsolation queue,
            @Value("${notarist.sidecar.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${notarist.runtime.embedding.model:bge-m3}") String model) {
        this.restTemplate = restTemplate;
        this.metrics      = metrics;
        this.degradation  = degradation;
        this.timeout      = timeout;
        this.queue        = queue;
        this.baseUrl      = baseUrl;
        this.model        = model;
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public String activeModel() {
        return model;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.builder()
                .embedding(true)
                .batch(true, 64)   // Ollama /api/embed accepts an input array
                .build();
    }

    @Override
    public RuntimeProviderHealth health() {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.EMBEDDING)) {
            return RuntimeProviderHealth.down(PROVIDER_ID, model, "EMBEDDING runtime marked degraded");
        }
        return RuntimeProviderHealth.up(PROVIDER_ID, model, "Ollama embeddings at " + baseUrl,
                Map.of("endpoint", baseUrl, "dimension", 1024));
    }

    @Override
    public float[] embed(String text, String traceId) {
        return embedBatch(List.of(text), traceId + "-single").get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String batchId) {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.EMBEDDING)) {
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException(
                    "Ollama embedding degraded, batchId=" + batchId, null);
        }
        if (queue.isSaturated()) {
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException(
                    "Embedding queue saturated, batchId=" + batchId, null);
        }

        long startMs = System.currentTimeMillis();
        try {
            List<float[]> result = queue.submit(() ->
                    timeout.submitWithTimeout(batchId, () -> callOllamaEmbed(texts, batchId), EMBEDDING_TIMEOUT_MS)
            ).get();

            metrics.recordInferenceLatency(ModelProvider.BGE_M3, System.currentTimeMillis() - startMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, false, null);
            return result;

        } catch (TimeoutCancellationOrchestrator.TimeoutException e) {
            metrics.recordTimeout(ModelProvider.BGE_M3);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, true, "timeout");
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException("Ollama embedding timeout batchId=" + batchId, e);
        } catch (EmbeddingRuntimeWorker.EmbeddingRuntimeException e) {
            throw e;
        } catch (Exception e) {
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, true, e.getMessage());
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException("Ollama embedding failed batchId=" + batchId, e);
        }
    }

    @Override
    public boolean isAvailable() {
        return !degradation.isDegraded(RuntimeDegradationManager.AiRuntime.EMBEDDING);
    }

    @SuppressWarnings("unchecked")
    private List<float[]> callOllamaEmbed(List<String> texts, String batchId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("model", model, "input", texts);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + EMBED_PATH, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null) {
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException("Ollama embed returned null batchId=" + batchId, null);
        }

        List<List<Number>> embeddings = (List<List<Number>>) result.get("embeddings");
        if (embeddings == null || embeddings.size() != texts.size()) {
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException(
                    "Ollama embed count mismatch: expected=" + texts.size()
                    + " actual=" + (embeddings == null ? 0 : embeddings.size()) + " batchId=" + batchId, null);
        }

        return embeddings.stream().map(vec -> {
            if (vec.size() != QdrantVectorPayload.REQUIRED_DIMENSION) {
                throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException(
                        "Ollama embed dimension mismatch: expected=" + QdrantVectorPayload.REQUIRED_DIMENSION
                        + " actual=" + vec.size() + " model=" + model + " batchId=" + batchId, null);
            }
            float[] arr = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).floatValue();
            return arr;
        }).toList();
    }
}
