package com.notarist.search.application.pipeline;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.search.application.port.out.KeywordSearchRepository;
import com.notarist.search.application.query.SearchQuery;
import com.notarist.search.domain.model.RetrievalReason;
import com.notarist.search.domain.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Retrieves chunks via PostgreSQL BM25 full-text search. */
@Service
public class KeywordRetriever {

    private static final Logger log = LoggerFactory.getLogger(KeywordRetriever.class);

    private final KeywordSearchRepository keywordSearchRepository;

    public KeywordRetriever(KeywordSearchRepository keywordSearchRepository) {
        this.keywordSearchRepository = keywordSearchRepository;
    }

    public List<RetrievedChunk> retrieve(String normalizedQuery, SearchQuery query) {
        String docTypeFilter = query.documentTypeFilter() != null
                ? query.documentTypeFilter().name() : null;

        List<KeywordSearchRepository.BM25SearchResult> results = keywordSearchRepository.search(
                normalizedQuery,
                query.tenantId(),
                query.maxClassificationLevel(),
                docTypeFilter,
                query.maxResults());

        log.debug("KeywordRetriever: {} results tenantId={}", results.size(), query.tenantId());
        return results.stream().map(this::toChunk).collect(Collectors.toList());
    }

    private RetrievedChunk toChunk(KeywordSearchRepository.BM25SearchResult r) {
        return new RetrievedChunk(
                r.chunkId(),
                new DocumentId(UUID.fromString(r.documentId())),
                UUID.fromString(r.tenantId()),
                JenisDokumen.valueOf(r.documentType()),
                ClassificationLevel.valueOf(r.classificationLevel()),
                r.chunkIndex(),
                r.sectionTitle(),
                r.pageNumber(),
                r.chunkText(),
                r.sourceObjectKey(),
                r.bm25Score(),
                0.0,
                0.0,
                r.bm25Score(),
                Set.of(RetrievalReason.KEYWORD_MATCH));
    }
}
