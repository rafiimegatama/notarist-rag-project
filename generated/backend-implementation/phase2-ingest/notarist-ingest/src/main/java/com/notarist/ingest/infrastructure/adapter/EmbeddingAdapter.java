package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.util.NotaristConstants;
import com.notarist.ingest.application.port.out.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stub adapter for bge-m3 embedding.
 * Returns zero-vector stubs (1024-dim) for pipeline testing.
 *
 * PHASE 6A.2-FIX: @Component REMOVED.
 * Production bean: EmbeddingRuntimeWorker in notarist-runtime.
 * Test usage: declare as @Bean in @TestConfiguration.
 */
public class EmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingAdapter.class);

    private final String ollamaUrl;

    public EmbeddingAdapter(String ollamaUrl) {
        this.ollamaUrl = ollamaUrl;
    }

    @Override
    public List<EmbeddingResult> generateEmbeddings(List<ChunkInput> chunks, EmbeddingConfig config) {
        log.info("[STUB] Embedding generateEmbeddings chunks={} model={} ollama={}",
                chunks.size(), config.model(), ollamaUrl);

        return chunks.stream()
                .map(chunk -> new EmbeddingResult(
                        chunk.chunkId(),
                        new float[NotaristConstants.EMBEDDING_DIMENSION],
                        50L
                ))
                .collect(Collectors.toList());
    }
}
