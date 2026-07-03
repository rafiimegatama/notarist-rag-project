package com.notarist.observability.degradation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapts the response strategy based on current degradation level.
 *
 * Called by AssistantOrchestrator and SearchController before executing the full pipeline.
 * Returns a ResponseStrategy that the orchestrator uses to decide what to attempt.
 *
 * Strategy mapping:
 *   FULL              → FULL_PIPELINE       — all features available
 *   DEGRADED          → REDUCED_QUALITY     — reranker skip, embedding may be slower
 *   LIMITED_SEARCH    → KEYWORD_ONLY        — BM25 only, no vector search
 *   SEARCH_ONLY       → SEARCH_NO_LLM      — return search results, no LLM synthesis
 *   EMERGENCY         → INGESTION_BLOCKED  — read-only mode; no new documents accepted
 *   CRITICAL          → READ_ONLY_BEST_EFFORT — attempt from existing index; warn user
 *
 * Produces ResponseStrategyDecision which includes:
 *   - what to attempt
 *   - user-visible degradation warning (in Indonesian, appropriate for end users)
 *   - whether to skip LLM
 *   - whether to skip reranker
 */
@Component
public class DegradationAwareResponseStrategy {

    private static final Logger log = LoggerFactory.getLogger(DegradationAwareResponseStrategy.class);

    public enum ResponseStrategyType {
        FULL_PIPELINE,
        REDUCED_QUALITY,
        KEYWORD_ONLY,
        SEARCH_NO_LLM,
        INGESTION_BLOCKED,
        READ_ONLY_BEST_EFFORT
    }

    public record ResponseStrategyDecision(
            ResponseStrategyType strategyType,
            boolean              skipLlm,
            boolean              skipReranker,
            boolean              skipVectorSearch,
            boolean              blockIngestion,
            String               userWarning
    ) {
        public boolean isFullyFunctional() {
            return strategyType == ResponseStrategyType.FULL_PIPELINE;
        }

        public static ResponseStrategyDecision full() {
            return new ResponseStrategyDecision(
                    ResponseStrategyType.FULL_PIPELINE, false, false, false, false, null);
        }
    }

    private final OperationalDegradationHierarchy degradationHierarchy;

    public DegradationAwareResponseStrategy(OperationalDegradationHierarchy degradationHierarchy) {
        this.degradationHierarchy = degradationHierarchy;
    }

    public ResponseStrategyDecision decide() {
        OperationalDegradationHierarchy.DegradationLevel level = degradationHierarchy.getActiveLevel();
        ResponseStrategyDecision decision = buildDecision(level);

        if (!decision.isFullyFunctional()) {
            log.warn("DegradationAwareResponseStrategy: degraded strategy={} level={}",
                    decision.strategyType(), level);
        }

        return decision;
    }

    private ResponseStrategyDecision buildDecision(OperationalDegradationHierarchy.DegradationLevel level) {
        return switch (level) {
            case FULL -> ResponseStrategyDecision.full();

            case DEGRADED -> new ResponseStrategyDecision(
                    ResponseStrategyType.REDUCED_QUALITY,
                    false, true, false, false,
                    "Kualitas pencarian mungkin sedikit berkurang karena layanan reranker sedang tidak optimal.");

            case LIMITED_SEARCH -> new ResponseStrategyDecision(
                    ResponseStrategyType.KEYWORD_ONLY,
                    false, true, true, false,
                    "Pencarian semantik sementara tidak tersedia. Hasil ditampilkan berdasarkan kata kunci saja.");

            case SEARCH_ONLY -> new ResponseStrategyDecision(
                    ResponseStrategyType.SEARCH_NO_LLM,
                    true, true, false, false,
                    "Asisten AI sementara tidak tersedia. Hasil pencarian dokumen tetap tersedia.");

            case EMERGENCY -> new ResponseStrategyDecision(
                    ResponseStrategyType.INGESTION_BLOCKED,
                    true, true, false, true,
                    "Sistem penyimpanan dokumen sedang tidak tersedia. " +
                    "Pencarian pada dokumen yang sudah ada tetap bisa dilakukan.");

            case CRITICAL -> new ResponseStrategyDecision(
                    ResponseStrategyType.READ_ONLY_BEST_EFFORT,
                    true, true, false, true,
                    "Sistem sedang mengalami gangguan kritis. " +
                    "Beberapa fitur mungkin tidak berfungsi. Tim IT sedang menangani masalah ini.");
        };
    }
}
