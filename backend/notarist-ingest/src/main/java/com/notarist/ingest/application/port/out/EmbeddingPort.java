package com.notarist.ingest.application.port.out;

import java.util.List;

/**
 * Output port for dense vector embedding generation.
 * Implemented by IngestEmbeddingRuntimeAdapter in notarist-runtime
 * (real bge-m3 batch HTTP call via EmbeddingRuntimeWorker).
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
