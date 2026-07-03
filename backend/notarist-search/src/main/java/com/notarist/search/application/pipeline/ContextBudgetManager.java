package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages LLM context token budget.
 * Prioritises high-score chunks, removes duplicates, truncates to budget.
 * Token estimation: character count / 1.3 (approximation for Indonesian + English mixed text).
 */
@Service
public class ContextBudgetManager {

    private static final double TOKEN_RATIO = 1.3;

    public BudgetResult applyBudget(List<RetrievedChunk> candidates, int tokenBudget) {
        List<RetrievedChunk> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::effectiveScore).reversed())
                .toList();

        List<RetrievedChunk> selected = new ArrayList<>();
        int usedTokens = 0;
        boolean truncated = false;

        for (RetrievedChunk chunk : sorted) {
            int chunkTokens = estimateTokens(chunk.text());
            if (usedTokens + chunkTokens > tokenBudget) {
                truncated = true;
                break;
            }
            selected.add(chunk);
            usedTokens += chunkTokens;
        }

        return new BudgetResult(selected, usedTokens, truncated, candidates.size());
    }

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() / TOKEN_RATIO);
    }

    public record BudgetResult(
            List<RetrievedChunk> selectedChunks,
            int estimatedTokenCount,
            boolean truncated,
            int totalCandidates
    ) {}
}
