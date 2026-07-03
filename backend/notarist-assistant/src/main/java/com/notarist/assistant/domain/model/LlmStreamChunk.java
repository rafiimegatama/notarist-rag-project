package com.notarist.assistant.domain.model;

/** One token or sentence fragment from a streaming LLM response. */
public record LlmStreamChunk(
        String chunkId,
        String delta,
        boolean done,
        int tokenIndex
) {}
