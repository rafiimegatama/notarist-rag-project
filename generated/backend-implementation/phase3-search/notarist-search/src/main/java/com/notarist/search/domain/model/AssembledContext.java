package com.notarist.search.domain.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Final assembled retrieval context — ready to be passed to LLM (Phase 4+).
 * Citations are resolved and grounding score computed before assembly.
 */
public record AssembledContext(
        List<RetrievedChunk> contextChunks,
        List<CitationEntry> citations,
        GroundingScore groundingScore,
        int estimatedTokenCount,
        boolean truncated,
        int totalChunksBeforeTruncation
) {
    public AssembledContext {
        contextChunks = List.copyOf(contextChunks);
        citations     = List.copyOf(citations);
    }

    /** Formats chunks as numbered plaintext sections for LLM consumption. */
    public String assembledText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contextChunks.size(); i++) {
            RetrievedChunk chunk = contextChunks.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
                sb.append("(").append(chunk.sectionTitle()).append(") ");
            }
            sb.append(chunk.text()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
