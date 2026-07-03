package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.application.port.out.SearchPort.RetrievedChunkDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages LLM context token budget for the assistant.
 *
 * Budget allocation (from contextTokenBudget):
 *   - SYSTEM_PROMPT_RESERVE (400 tokens): system instructions + prompt template
 *   - CITATION_RESERVE (600 tokens): citation section appended after context
 *   - Remaining budget: ranked chunks, sorted by relevance
 *
 * Prioritization order (before score sort): REGULASI > AKTA > SOP
 * Deduplication: chunks sharing the same sourceObjectKey prefix (first 80 chars) — keep highest-score only.
 */
@Service
public class AssistantContextBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(AssistantContextBudgetManager.class);

    private static final double TOKEN_RATIO        = 1.3;
    private static final int    SYSTEM_PROMPT_RESERVE = 400;
    private static final int    CITATION_RESERVE      = 600;

    private static final Map<String, Integer> DOC_TYPE_PRIORITY = Map.of(
            "REGULASI", 0, "AKTA", 1, "SOP", 2);

    public BudgetResult applyBudget(List<RetrievedChunkDto> candidates, int contextTokenBudget) {
        int availableForChunks = Math.max(0, contextTokenBudget - SYSTEM_PROMPT_RESERVE - CITATION_RESERVE);

        // Deduplicate by sourceObjectKey prefix
        List<RetrievedChunkDto> deduped = deduplicate(candidates);

        // Sort: doc type priority first, then relevance score descending
        List<RetrievedChunkDto> sorted = deduped.stream()
                .sorted(Comparator
                        .comparingInt((RetrievedChunkDto c) ->
                                DOC_TYPE_PRIORITY.getOrDefault(c.documentType(), 99))
                        .thenComparingDouble(RetrievedChunkDto::relevanceScore).reversed())
                .collect(Collectors.toList());

        List<RetrievedChunkDto> selected = new ArrayList<>();
        int usedTokens = 0;
        boolean truncated = false;

        for (RetrievedChunkDto chunk : sorted) {
            int chunkTokens = estimateTokens(chunk.chunkText());
            if (usedTokens + chunkTokens > availableForChunks) {
                truncated = true;
                break;
            }
            selected.add(chunk);
            usedTokens += chunkTokens;
        }

        log.debug("ContextBudget: {} candidates → {} deduped → {} selected ({} tokens, truncated={})",
                candidates.size(), deduped.size(), selected.size(), usedTokens, truncated);

        return new BudgetResult(selected, usedTokens, truncated, candidates.size(), deduped.size());
    }

    private List<RetrievedChunkDto> deduplicate(List<RetrievedChunkDto> chunks) {
        Map<String, RetrievedChunkDto> seen = new LinkedHashMap<>();
        for (RetrievedChunkDto chunk : chunks) {
            String key = sourcePrefix(chunk.sourceObjectKey());
            seen.merge(key, chunk,
                    (existing, incoming) ->
                            incoming.relevanceScore() > existing.relevanceScore() ? incoming : existing);
        }
        return new ArrayList<>(seen.values());
    }

    private String sourcePrefix(String sourceObjectKey) {
        if (sourceObjectKey == null) return "";
        return sourceObjectKey.length() > 80 ? sourceObjectKey.substring(0, 80) : sourceObjectKey;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() / TOKEN_RATIO);
    }

    public record BudgetResult(
            List<RetrievedChunkDto> selectedChunks,
            int estimatedTokenCount,
            boolean truncated,
            int totalCandidates,
            int afterDedup
    ) {}
}
