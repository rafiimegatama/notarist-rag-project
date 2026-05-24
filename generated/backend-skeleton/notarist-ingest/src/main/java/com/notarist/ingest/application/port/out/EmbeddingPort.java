package com.notarist.ingest.application.port.out;

import com.notarist.core.util.NotaristConstants;

import java.util.List;

/** Port for bge-m3 embedding via Ollama — :11434. Dimension fixed at 1024. */
public interface EmbeddingPort {

    List<EmbeddingResult> generateEmbeddings(List<ChunkInput> chunks, EmbeddingConfig config);

    record ChunkInput(String chunkId, String text) {}

    record EmbeddingConfig(String model, int dimension, int batchSize) {
        public static EmbeddingConfig defaultBgeM3() {
            return new EmbeddingConfig("bge-m3", NotaristConstants.EMBEDDING_DIMENSION, 16);
        }
    }

    record EmbeddingResult(
        String chunkId,
        float[] vector,
        long processingMs
    ) {
        public EmbeddingResult {
            if (vector.length != NotaristConstants.EMBEDDING_DIMENSION) {
                throw new IllegalArgumentException(
                    "Embedding vector must be " + NotaristConstants.EMBEDDING_DIMENSION + "-dimensional");
            }
        }
    }
}
