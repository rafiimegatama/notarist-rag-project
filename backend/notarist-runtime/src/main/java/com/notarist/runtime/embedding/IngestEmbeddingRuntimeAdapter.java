package com.notarist.runtime.embedding;

import com.notarist.ingest.application.port.out.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Real EmbeddingPort implementation for ingest-time chunk embedding —
 * delegates to EmbeddingRuntimeWorker (bge-m3 HTTP adapter, batch endpoint).
 *
 * Mirrors the QueryEmbeddingRuntimeAdapter inversion pattern: the port is
 * owned by the consuming module (notarist-ingest), the real implementation
 * lives here in notarist-runtime.
 *
 * EmbeddingRuntimeWorker.embedBatch(...) throws EmbeddingRuntimeException on
 * failure (degraded service, queue saturation, timeout, dimension mismatch);
 * this adapter lets that propagate — EmbeddingWorker wraps it into a
 * retryable IngestionStageException so the pipeline retry/DLQ machinery
 * handles it.
 */
@Component
public class IngestEmbeddingRuntimeAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(IngestEmbeddingRuntimeAdapter.class);

    private final EmbeddingRuntimeWorker embeddingRuntimeWorker;

    public IngestEmbeddingRuntimeAdapter(EmbeddingRuntimeWorker embeddingRuntimeWorker) {
        this.embeddingRuntimeWorker = embeddingRuntimeWorker;
    }

    @Override
    public List<EmbeddingResult> generateEmbeddings(List<ChunkInput> chunks, EmbeddingConfig config) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<String> texts = chunks.stream().map(ChunkInput::text).toList();
        String batchId = "ingest-" + chunks.get(0).chunkId() + "-n" + chunks.size();

        long startMs = System.currentTimeMillis();
        List<float[]> vectors = embeddingRuntimeWorker.embedBatch(texts, batchId);
        long durationMs = System.currentTimeMillis() - startMs;

        if (vectors.size() != chunks.size()) {
            throw new EmbeddingRuntimeWorker.EmbeddingRuntimeException(
                    "Embedding count mismatch: expected=" + chunks.size()
                    + " actual=" + vectors.size() + " batchId=" + batchId, null);
        }

        List<EmbeddingResult> results = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            results.add(new EmbeddingResult(chunks.get(i).chunkId(), vectors.get(i), durationMs));
        }

        log.debug("IngestEmbeddingRuntimeAdapter: embedded {} chunks batchId={} durationMs={}",
                results.size(), batchId, durationMs);
        return results;
    }
}
