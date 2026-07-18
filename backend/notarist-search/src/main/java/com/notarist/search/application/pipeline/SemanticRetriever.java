package com.notarist.search.application.pipeline;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.core.util.NotaristConstants;
import com.notarist.search.application.port.out.QueryEmbeddingPort;
import com.notarist.search.application.port.out.VectorSearchPort;
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

/**
 * Retrieves chunks via Qdrant vector similarity search.
 * Query text is embedded through QueryEmbeddingPort (real bge-m3 encoding via
 * EmbeddingRuntimeWorker in notarist-runtime). If embedding fails (degraded
 * service, timeout, etc.) this retriever degrades gracefully to zero semantic
 * hits rather than failing the whole search — keyword retrieval still runs.
 */
@Service
public class SemanticRetriever {

    private static final Logger log = LoggerFactory.getLogger(SemanticRetriever.class);
    // bge-m3 query↔passage cosine for a directly relevant chunk measures ~0.55-0.60 (verified
    // against a live index: the ingested akta the query asked about scored 0.574). 0.60 was
    // above the model's real operating range and filtered out correct answers; 0.45 admits
    // relevant candidates and leaves precision to RRF fusion, reranking and grounding.
    private static final float MIN_COSINE_SCORE = 0.45f;

    private final VectorSearchPort   vectorSearchPort;
    private final QueryEmbeddingPort queryEmbeddingPort;

    public SemanticRetriever(VectorSearchPort vectorSearchPort, QueryEmbeddingPort queryEmbeddingPort) {
        this.vectorSearchPort = vectorSearchPort;
        this.queryEmbeddingPort = queryEmbeddingPort;
    }

    public List<RetrievedChunk> retrieve(String normalizedQuery, SearchQuery query) {
        float[] queryVector = embedQuery(normalizedQuery, query);
        if (queryVector == null) {
            return List.of();
        }

        String docTypeFilter = query.documentTypeFilter() != null
                ? query.documentTypeFilter().name() : null;

        List<VectorSearchPort.VectorSearchResult> results = vectorSearchPort.search(
                queryVector,
                query.tenantId(),
                query.maxClassificationLevel(),
                docTypeFilter,
                query.maxResults(),
                MIN_COSINE_SCORE);

        log.debug("SemanticRetriever: {} results tenantId={}", results.size(), query.tenantId());
        return results.stream().map(this::toChunk).collect(Collectors.toList());
    }

    /**
     * Embeds the normalized query text. Returns null (rather than throwing) on any
     * embedding failure so the caller can degrade to zero semantic hits — a failed
     * embedding call must never fail the whole search, since keyword retrieval runs
     * independently and can still serve results.
     */
    private float[] embedQuery(String normalizedQuery, SearchQuery query) {
        try {
            float[] vector = queryEmbeddingPort.embedQuery(normalizedQuery, query.correlationId().value());
            if (vector == null || vector.length != NotaristConstants.EMBEDDING_DIMENSION) {
                log.warn("SemanticRetriever: invalid query embedding (null or dimension mismatch) queryId={}",
                        query.queryId());
                return null;
            }
            return vector;
        } catch (Exception e) {
            log.warn("SemanticRetriever: query embedding failed queryId={} — degrading to zero semantic hits: {}",
                    query.queryId(), e.getMessage());
            return null;
        }
    }

    private RetrievedChunk toChunk(VectorSearchPort.VectorSearchResult r) {
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
                0.0,
                r.cosineScore(),
                0.0,
                r.cosineScore(),
                Set.of(RetrievalReason.SEMANTIC_MATCH));
    }
}
