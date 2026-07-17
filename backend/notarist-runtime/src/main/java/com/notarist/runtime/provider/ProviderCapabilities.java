package com.notarist.runtime.provider;

/**
 * What an AI provider/model can actually do. The runtime queries this instead of hardcoding
 * assumptions about a backend, so routing decisions (stream vs. block, batch vs. loop, allow
 * tool-calling or not) are made from declared capabilities rather than {@code instanceof} checks or
 * provider-name string matching.
 *
 * <p>Mirrors the design of {@code com.notarist.runtime.ocr.spi.OcrCapabilities} so the whole runtime
 * module reasons about capabilities uniformly across OCR, LLM, embedding and reranking.
 *
 * <p>Every flag defaults to the conservative answer ({@code false}) via the factory helpers — a new
 * provider advertises only what it has verified it supports, and an un-declared capability is
 * treated as absent rather than assumed present.
 *
 * @param supportsStreaming      token-by-token streaming (SSE/NDJSON) is available
 * @param supportsVision         accepts image inputs (multimodal)
 * @param supportsToolCalling    supports function/tool calling
 * @param supportsJsonMode       can be constrained to emit valid JSON / a schema
 * @param supportsEmbedding      can produce dense embedding vectors
 * @param supportsReranking      can score query/passage relevance (cross-encoder style)
 * @param supportsThinking       exposes explicit reasoning/"thinking" traces (e.g. qwen3, o-series)
 * @param supportsBatchInference can process several inputs in one call, positionally aligned
 * @param maxBatchSize           hard ceiling for a batch call; 1 means "no batching"
 */
public record ProviderCapabilities(
        boolean supportsStreaming,
        boolean supportsVision,
        boolean supportsToolCalling,
        boolean supportsJsonMode,
        boolean supportsEmbedding,
        boolean supportsReranking,
        boolean supportsThinking,
        boolean supportsBatchInference,
        int maxBatchSize
) {

    public ProviderCapabilities {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("maxBatchSize must be >= 1 (1 means: no batching)");
        }
        if (!supportsBatchInference && maxBatchSize != 1) {
            throw new IllegalArgumentException(
                    "maxBatchSize must be 1 when supportsBatchInference is false");
        }
    }

    /** Mutable-feeling fluent builder — keeps call sites readable given nine fields. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean streaming;
        private boolean vision;
        private boolean toolCalling;
        private boolean jsonMode;
        private boolean embedding;
        private boolean reranking;
        private boolean thinking;
        private boolean batch;
        private int maxBatchSize = 1;

        public Builder streaming(boolean v)   { this.streaming = v; return this; }
        public Builder vision(boolean v)      { this.vision = v; return this; }
        public Builder toolCalling(boolean v) { this.toolCalling = v; return this; }
        public Builder jsonMode(boolean v)    { this.jsonMode = v; return this; }
        public Builder embedding(boolean v)   { this.embedding = v; return this; }
        public Builder reranking(boolean v)   { this.reranking = v; return this; }
        public Builder thinking(boolean v)    { this.thinking = v; return this; }
        public Builder batch(boolean v, int maxBatchSize) {
            this.batch = v;
            this.maxBatchSize = v ? Math.max(2, maxBatchSize) : 1;
            return this;
        }

        public ProviderCapabilities build() {
            return new ProviderCapabilities(streaming, vision, toolCalling, jsonMode,
                    embedding, reranking, thinking, batch, maxBatchSize);
        }
    }
}
