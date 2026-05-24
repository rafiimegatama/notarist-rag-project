package com.notarist.runtime.model;

/**
 * Immutable model metadata record.
 *
 * All fields required for:
 *   - runtime routing (provider + endpointUrl)
 *   - embedding dimension validation (embeddingDimension)
 *   - context overflow guard (maxContextWindow)
 *   - audit + reproducibility (version + checksum)
 *
 * checksum: SHA-256 of the model weights file — used to detect model swaps.
 *           "unverified" if checksum cannot be obtained from the runtime.
 */
public record ModelDefinition(
        String        modelName,
        String        version,
        ModelProvider provider,
        String        endpointUrl,
        int           embeddingDimension,
        String        tokenizer,
        int           maxContextWindow,
        int           maxOutputTokens,
        String        checksum
) {
    public ModelDefinition {
        if (modelName == null || modelName.isBlank())   throw new IllegalArgumentException("modelName required");
        if (version   == null || version.isBlank())     throw new IllegalArgumentException("version required");
        if (provider  == null)                           throw new IllegalArgumentException("provider required");
        if (endpointUrl == null || endpointUrl.isBlank()) throw new IllegalArgumentException("endpointUrl required");
        if (maxContextWindow <= 0)  maxContextWindow  = 4096;
        if (maxOutputTokens  <= 0)  maxOutputTokens   = 2048;
        if (checksum == null || checksum.isBlank()) checksum = "unverified";
    }

    public boolean isEmbeddingModel() {
        return provider == ModelProvider.BGE_M3;
    }

    public boolean isLlmModel() {
        return provider == ModelProvider.OLLAMA;
    }
}
