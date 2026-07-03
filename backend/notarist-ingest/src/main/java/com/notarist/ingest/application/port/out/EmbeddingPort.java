package com.notarist.ingest.application.port.out;

import java.util.List;

/**
 * Output port for dense vector embedding generation.
 * Implemented by EmbeddingAdapter (bge-m3 via Ollama stub in Phase 2).
 * Real Ollama HTTP invocation deferred to Phase 2C when chunk text is available.
 */
public interface EmbeddingPort {

    List<EmbeddingResult> generateEmbeddings(List<ChunkInput> chunks, EmbeddingConfig config);

    record ChunkInput(
            String chunkId,
            String text
    ) {}

    record EmbeddingResult(
            String chunkId,
            float[] vector,
            long durationMs
    ) {}

    record EmbeddingConfig(
            String model,
            int dimension
    ) {
        public static EmbeddingConfig defaultBgeM3() {
            return new EmbeddingConfig("bge-m3", 1024);
        }
    }
}
