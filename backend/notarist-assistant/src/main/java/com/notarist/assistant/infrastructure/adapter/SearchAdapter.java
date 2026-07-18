package com.notarist.assistant.infrastructure.adapter;

import com.notarist.assistant.application.port.out.SearchPort;
import com.notarist.assistant.domain.model.AnswerConfidence;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.search.api.response.CitationResponse;
import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.application.port.in.SearchUseCase;
import com.notarist.search.application.query.SearchQuery;
import com.notarist.search.domain.model.GroundingScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Real search adapter — calls notarist-search's SearchUseCase in-process.
 *
 * Translates the assistant's SearchPort request/response shapes to/from the
 * search module's SearchQuery / SearchResponse. Note that SearchResponse only
 * exposes resolved citations (chunkId, documentId, documentType, a truncated
 * ~200-char excerpt, sourceObjectKey, chunkIndex, relevanceScore) — it does not
 * leak per-chunk tenantId/classificationLevel/sectionTitle/pageNumber/full text
 * across the module boundary (mirrors what SearchController exposes over REST).
 * Those unavailable fields are filled with the best available request-scoped
 * equivalent (tenantId, maxClassificationLevel) or left null, matching how
 * RetrievalContextAssembler/CitationInjector already tolerate null sectionTitle
 * and pageNumber.
 */
@Component
public class SearchAdapter implements SearchPort {

    private static final Logger log = LoggerFactory.getLogger(SearchAdapter.class);

    private final SearchUseCase searchUseCase;

    public SearchAdapter(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @Override
    public SearchResult search(AssistantSearchRequest request) {
        SearchQuery query = toSearchQuery(request);

        log.debug("SearchAdapter: delegating to SearchUseCase queryId={} tenantId={} query='{}'",
                query.queryId(), request.tenantId(), request.query());

        SearchResponse response = searchUseCase.search(query);

        if (!"SUCCESS".equals(response.status())) {
            log.warn("SearchAdapter: search failed queryId={} error={}",
                    query.queryId(), response.errorMessage());
            return SearchResult.empty();
        }

        return toSearchResult(request, response);
    }

    private SearchQuery toSearchQuery(AssistantSearchRequest request) {
        ClassificationLevel maxLevel = request.maxClassificationLevel() != null
                ? ClassificationLevel.valueOf(request.maxClassificationLevel())
                : ClassificationLevel.INTERNAL;

        JenisDokumen documentTypeFilter = null;
        if (request.documentTypeFilter() != null && !request.documentTypeFilter().isBlank()) {
            try {
                documentTypeFilter = JenisDokumen.valueOf(request.documentTypeFilter());
            } catch (IllegalArgumentException e) {
                log.warn("SearchAdapter: unknown documentTypeFilter='{}' — ignoring", request.documentTypeFilter());
            }
        }

        CorrelationId correlationId = request.correlationId() != null
                ? CorrelationId.of(request.correlationId().toString())
                : CorrelationId.generate();

        return new SearchQuery(
                UUID.randomUUID(),
                request.query(),
                request.tenantId(),
                request.userId(),
                maxLevel,
                documentTypeFilter,
                null,
                request.maxResults(),
                request.contextTokenBudget(),
                correlationId);
    }

    private SearchResult toSearchResult(AssistantSearchRequest request, SearchResponse response) {
        List<RetrievedChunkDto> chunks = response.citations().stream()
                .map(citation -> toChunkDto(request, citation))
                .collect(Collectors.toList());

        return new SearchResult(
                chunks,
                toAnswerConfidence(response.groundingLevel()),
                response.groundingOverallScore(),
                request.correlationId() != null ? request.correlationId() : UUID.randomUUID(),
                response.retrievedChunkCount());
    }

    private RetrievedChunkDto toChunkDto(AssistantSearchRequest request, CitationResponse citation) {
        return new RetrievedChunkDto(
                citation.chunkId(),
                citation.documentId().toString(),
                request.tenantId().toString(),
                citation.sourceType(),
                request.maxClassificationLevel(),
                citation.chunkIndex(),
                null,
                null,
                // Full chunk text, not the ~200-char citation excerpt: this DTO feeds
                // RetrievalContextAssembler, and a truncated context makes the LLM refuse
                // ("konteks tidak mencukupi") on questions the document plainly answers.
                citation.chunkText() != null && !citation.chunkText().isBlank()
                        ? citation.chunkText() : citation.citationText(),
                citation.sourceObjectKey(),
                citation.relevanceScore());
    }

    private AnswerConfidence toAnswerConfidence(GroundingScore.Level level) {
        if (level == null) return AnswerConfidence.INSUFFICIENT;
        return switch (level) {
            case HIGH -> AnswerConfidence.HIGH;
            case MEDIUM -> AnswerConfidence.MEDIUM;
            case LOW -> AnswerConfidence.LOW;
            case UNGROUNDED -> AnswerConfidence.INSUFFICIENT;
        };
    }
}
