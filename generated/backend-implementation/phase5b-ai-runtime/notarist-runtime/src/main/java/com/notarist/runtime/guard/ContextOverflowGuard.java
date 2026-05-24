package com.notarist.runtime.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Prevents context window overflow before LLM calls.
 *
 * Token estimation: chars / 1.3 (conservative for Indonesian + legal text).
 * Hard limit: maxContextWindow - RESERVED_TOKENS (512) for system prompt + response headroom.
 *
 * Truncation priority (highest first):
 *   1. REGULASI chunks — keep first
 *   2. Chunks with [Sumber: X] citation markers — keep second
 *   3. Other chunks — drop last
 *
 * Caller must provide chunks in retrieval-ranked order; guard preserves rank within priority class.
 */
@Component
public class ContextOverflowGuard {

    private static final Logger log = LoggerFactory.getLogger(ContextOverflowGuard.class);

    private static final int RESERVED_TOKENS    = 512;
    private static final double CHARS_PER_TOKEN = 1.3;

    public record ContextChunk(
            String chunkId,
            String documentType,
            String text,
            boolean hasCitationMarker
    ) {
        public boolean isRegulasi() {
            return "REGULASI".equalsIgnoreCase(documentType)
                    || "PERATURAN".equalsIgnoreCase(documentType)
                    || "UU".equalsIgnoreCase(documentType);
        }
    }

    public record OverflowResult(
            List<ContextChunk> truncatedChunks,
            Set<String>        preservedChunkIds,
            int                droppedChunkCount,
            int                estimatedTokens,
            boolean            wasOverflowed
    ) {
        public static OverflowResult noOverflow(List<ContextChunk> chunks, int estimatedTokens) {
            Set<String> ids = new LinkedHashSet<>();
            for (ContextChunk c : chunks) ids.add(c.chunkId());
            return new OverflowResult(chunks, ids, 0, estimatedTokens, false);
        }
    }

    /**
     * @param chunks          ordered list of candidate context chunks (highest relevance first)
     * @param maxContextWindow model's context window in tokens (e.g. 8192 for llama3.2)
     * @param systemPromptText full system prompt text (to pre-deduct its token cost)
     * @return truncated chunk list guaranteed to fit within the budget
     */
    public OverflowResult guard(List<ContextChunk> chunks, int maxContextWindow, String systemPromptText) {
        int systemTokens = estimateTokens(systemPromptText);
        int budget = maxContextWindow - RESERVED_TOKENS - systemTokens;

        if (budget <= 0) {
            log.error("ContextOverflowGuard: system prompt alone exceeds context budget (systemTokens={})", systemTokens);
            return new OverflowResult(List.of(), Set.of(), chunks.size(), systemTokens, true);
        }

        int totalTokens = 0;
        for (ContextChunk c : chunks) {
            totalTokens += estimateTokens(c.text());
        }

        if (totalTokens <= budget) {
            return OverflowResult.noOverflow(chunks, systemTokens + totalTokens);
        }

        log.warn("ContextOverflowGuard: overflow detected estimatedTokens={} budget={} chunkCount={}",
                totalTokens, budget, chunks.size());

        List<ContextChunk> result  = new ArrayList<>();
        Set<String>        kept    = new LinkedHashSet<>();
        int used = 0;

        // Pass 1: REGULASI chunks first
        for (ContextChunk c : chunks) {
            if (!c.isRegulasi()) continue;
            int tokens = estimateTokens(c.text());
            if (used + tokens <= budget) {
                result.add(c);
                kept.add(c.chunkId());
                used += tokens;
            }
        }

        // Pass 2: chunks with citation markers
        for (ContextChunk c : chunks) {
            if (kept.contains(c.chunkId())) continue;
            if (!c.hasCitationMarker())     continue;
            int tokens = estimateTokens(c.text());
            if (used + tokens <= budget) {
                result.add(c);
                kept.add(c.chunkId());
                used += tokens;
            }
        }

        // Pass 3: remaining chunks in ranked order
        for (ContextChunk c : chunks) {
            if (kept.contains(c.chunkId())) continue;
            int tokens = estimateTokens(c.text());
            if (used + tokens <= budget) {
                result.add(c);
                kept.add(c.chunkId());
                used += tokens;
            }
        }

        int dropped = chunks.size() - result.size();
        log.info("ContextOverflowGuard: truncated chunk={} dropped={} finalTokens={}",
                result.size(), dropped, systemTokens + used);

        return new OverflowResult(Collections.unmodifiableList(result), kept, dropped,
                systemTokens + used, true);
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
