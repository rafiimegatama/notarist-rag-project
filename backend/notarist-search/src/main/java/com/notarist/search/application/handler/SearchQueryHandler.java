package com.notarist.search.application.handler;

import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.application.pipeline.*;
import com.notarist.search.application.port.in.SearchUseCase;
import com.notarist.search.application.query.SearchQuery;
import com.notarist.search.domain.model.AssembledContext;
import com.notarist.search.domain.model.RetrievedChunk;
import com.notarist.search.domain.model.SearchIntent;
import com.notarist.search.infrastructure.metrics.SearchMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the full retrieval pipeline.
 * NOT a mega service — each stage is delegated to its dedicated component.
 *
 * Pipeline:
 *   classify → normalize → parallel retrieval (keyword + semantic)
 *   → security filter (per source, BEFORE merge) → RRF fusion
 *   → diversity filter → rerank → context assembly (citations + grounding + budget)
 */
@Service
public class SearchQueryHandler implements SearchUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryHandler.class);

    private static final ExecutorService RETRIEVAL_POOL = Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "retrieval-pool"); t.setDaemon(true); return t; });

    private final IntentClassifier      intentClassifier;
    private final QueryNormalizer       queryNormalizer;
    private final SecurityFilterService securityFilter;
    private final KeywordRetriever      keywordRetriever;
    private final SemanticRetriever     semanticRetriever;
    private final RetrievalFusionService fusionService;
    private final DiversityFilterService diversityFilter;
    private final RerankerService       rerankerService;
    private final ContextAssemblyService contextAssembly;
    private final SearchMetricsRegistry metrics;

    public SearchQueryHandler(
            IntentClassifier intentClassifier,
            QueryNormalizer queryNormalizer,
            SecurityFilterService securityFilter,
            KeywordRetriever keywordRetriever,
            SemanticRetriever semanticRetriever,
            RetrievalFusionService fusionService,
            DiversityFilterService diversityFilter,
            RerankerService rerankerService,
            ContextAssemblyService contextAssembly,
            SearchMetricsRegistry metrics) {
        this.intentClassifier = intentClassifier;
        this.queryNormalizer  = queryNormalizer;
        this.securityFilter   = securityFilter;
        this.keywordRetriever = keywordRetriever;
        this.semanticRetriever = semanticRetriever;
        this.fusionService    = fusionService;
        this.diversityFilter  = diversityFilter;
        this.rerankerService  = rerankerService;
        this.contextAssembly  = contextAssembly;
        this.metrics          = metrics;
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        long startMs = System.currentTimeMillis();
        MDC.put("queryId",  query.queryId().toString());
        MDC.put("tenantId", query.tenantId().toString());

        try {
            // 1. Classify intent
            SearchIntent intent = intentClassifier.classify(query);
            metrics.recordQueryStarted(intent);
            log.info("Search started queryId={} intent={}", query.queryId(), intent);

            // 2. Normalize query
            String normalizedQuery = queryNormalizer.normalize(query.rawQuery());
            log.debug("Normalized: '{}' → '{}'", query.rawQuery(), normalizedQuery);

            // 3. Parallel retrieval
            CompletableFuture<List<RetrievedChunk>> kwFuture = CompletableFuture.supplyAsync(
                    () -> keywordRetriever.retrieve(normalizedQuery, query), RETRIEVAL_POOL);
            CompletableFuture<List<RetrievedChunk>> semFuture = CompletableFuture.supplyAsync(
                    () -> semanticRetriever.retrieve(normalizedQuery, query), RETRIEVAL_POOL);

            List<RetrievedChunk> kwResults  = kwFuture.join();
            List<RetrievedChunk> semResults = semFuture.join();

            // 4. Security filter — applied to each source BEFORE merge
            List<RetrievedChunk> safeKw  = securityFilter.filter(kwResults,  query.tenantId(), query.maxClassificationLevel());
            List<RetrievedChunk> safeSem = securityFilter.filter(semResults, query.tenantId(), query.maxClassificationLevel());

            // 5. RRF fusion
            List<RetrievedChunk> fused = fusionService.fuse(safeKw, safeSem);
            log.debug("Post-fusion: {} chunks", fused.size());

            // 6. Diversity filter
            List<RetrievedChunk> diverse = diversityFilter.filter(fused, query.maxResults());

            // 7. Rerank (stub passthrough in Phase 3)
            List<RetrievedChunk> reranked = rerankerService.rerank(normalizedQuery, diverse);

            // 8. Context assembly: citations → grounding → budget
            AssembledContext context = contextAssembly.assemble(
                    normalizedQuery, reranked, query.contextTokenBudget());

            long processingMs = System.currentTimeMillis() - startMs;
            metrics.recordQueryCompleted(intent, context.groundingScore().level(), processingMs);

            log.info("Search done queryId={} intent={} chunks={} grounding={} ms={}",
                    query.queryId(), intent, context.contextChunks().size(),
                    context.groundingScore().level(), processingMs);

            return SearchResponse.success(query, intent, normalizedQuery, context, processingMs);

        } catch (Exception e) {
            long processingMs = System.currentTimeMillis() - startMs;
            metrics.recordQueryFailed();
            log.error("Search failed queryId={}: {}", query.queryId(), e.getMessage(), e);
            return SearchResponse.error(query, e.getMessage(), processingMs);
        } finally {
            MDC.remove("queryId");
            MDC.remove("tenantId");
        }
    }
}
