package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.util.NotaristConstants;
import com.notarist.ingest.application.port.out.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stub adapter for bge-m3 via Ollama on :11434.
 * Returns zero-vector stubs (1024-dim). Replace with real Ollama HTTP call in Phase 2C.
 */
@Component
public class EmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingAdapter.class);

    private final String ollamaUrl;

    public EmbeddingAdapter(
            @Value("${notarist.ingest.embedding.ollama-url:http://localhost:11434}") String ollamaUrl) {
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
