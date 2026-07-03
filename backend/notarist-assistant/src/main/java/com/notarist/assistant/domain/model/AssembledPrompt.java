package com.notarist.assistant.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Result of PromptBuilder — the complete prompt to be sent to the LLM.
 * Carries citation chunk IDs so HallucinationGuard can cross-reference.
 */
public record AssembledPrompt(
        String systemPrompt,
        String userPrompt,
        PromptVersion version,
        UUID retrievalSnapshotId,
        int estimatedSystemTokens,
        int estimatedUserTokens,
        int totalEstimatedTokens,
        List<String> injectedChunkIds
) {}
