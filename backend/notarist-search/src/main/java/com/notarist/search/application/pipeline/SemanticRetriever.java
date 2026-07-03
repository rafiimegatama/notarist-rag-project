package com.notarist.search.application.pipeline;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.core.util.NotaristConstants;
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
 * Query vector is a zero stub — real bge-m3 encoding in Phase 2C.
 */
@Service
public class SemanticRetriever {

    private static final Logger log = LoggerFactory.getLogger(SemanticRetriever.class);
    private static final float MIN_COSINE_SCORE = 0.60f;

    private final VectorSearchPort vectorSearchPort;

    public SemanticRetriever(VectorSearchPort vectorSearchPort) {
        this.vectorSearchPort = vectorSearchPort;
    }

    public List<RetrievedChunk> retrieve(String normalizedQuery, SearchQuery query) {
        float[] queryVector = new float[NotaristConstants.EMBEDDING_DIMENSION]; // stub zero-vector
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
