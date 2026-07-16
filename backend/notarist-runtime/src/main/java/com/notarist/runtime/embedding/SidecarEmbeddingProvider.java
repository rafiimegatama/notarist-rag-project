package com.notarist.runtime.embedding;

import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.model.ModelRegistry;
import com.notarist.runtime.provider.EmbeddingProvider;
import com.notarist.runtime.provider.ProviderCapabilities;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code sidecar} embedding provider — the dedicated bge-m3 HTTP sidecar (POST /embed at
 * {@code notarist.sidecar.embedding.base-url}). Thin wrapper over {@link EmbeddingRuntimeWorker},
 * which owns the queue isolation, timeout, dimension validation and degradation bookkeeping.
 *
 * <p>Select with {@code EMBED_PROVIDER=sidecar}. This is the right choice when embeddings are
 * served by a separate GPU service rather than co-located in Ollama.
 */
@Component
public class SidecarEmbeddingProvider implements EmbeddingProvider {

    private static final String PROVIDER_ID = "sidecar";

    private final EmbeddingRuntimeWorker worker;
    private final RuntimeDegradationManager degradation;
    private final ModelRegistry modelRegistry;

    public SidecarEmbeddingProvider(EmbeddingRuntimeWorker worker,
                                    RuntimeDegradationManager degradation,
                                    ModelRegistry modelRegistry) {
        this.worker = worker;
        this.degradation = degradation;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public String activeModel() {
        return modelRegistry.getEmbedding().modelName();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.builder()
                .embedding(true)
                .batch(true, 32)
                .build();
    }

    @Override
    public RuntimeProviderHealth health() {
        String model = activeModel();
        String endpoint = modelRegistry.getEmbedding().endpointUrl();
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.EMBEDDING)) {
            return RuntimeProviderHealth.down(PROVIDER_ID, model, "EMBEDDING runtime marked degraded");
        }
        return RuntimeProviderHealth.up(PROVIDER_ID, model, "sidecar at " + endpoint,
                Map.of("endpoint", endpoint, "dimension", 1024));
    }

    @Override
    public float[] embed(String text, String traceId) {
        return worker.embed(text, traceId);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String batchId) {
        return worker.embedBatch(texts, batchId);
    }

    @Override
    public boolean isAvailable() {
        return !degradation.isDegraded(RuntimeDegradationManager.AiRuntime.EMBEDDING);
    }
}
