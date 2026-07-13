package com.notarist.search.infrastructure.persistence.postgres;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.search.application.port.out.KeywordSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL BM25 full-text retrieval via ts_rank + plainto_tsquery.
 * Uses 'simple' dictionary (no Indonesian stemmer required).
 * Classification level ordinal enforced in SQL — no post-filter drift.
 */
@Repository
public class BM25SearchRepositoryImpl implements KeywordSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(BM25SearchRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public BM25SearchRepositoryImpl(@Qualifier("postgresJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<BM25SearchResult> search(
            String normalizedQuery,
            UUID tenantId,
            ClassificationLevel maxClassificationLevel,
            String documentTypeFilter,
            int limit) {

        int maxLevelOrdinal = maxClassificationLevel.ordinal();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    ci.chunk_id,
                    ci.document_id::text            AS document_id,
                    ci.tenant_id::text              AS tenant_id,
                    ci.document_type,
                    ci.classification_level,
                    ci.chunk_index,
                    ci.section_title,
                    ci.page_number,
                    ci.chunk_text,
                    ci.source_object_key,
                    ts_rank(ci.search_vector, plainto_tsquery('simple', ?)) AS bm25_score
                FROM chunk_index ci
                WHERE ci.tenant_id = ?::uuid
                  AND ci.searchable = TRUE
                  AND ci.search_vector @@ plainto_tsquery('simple', ?)
                  AND CASE ci.classification_level
                        WHEN 'PUBLIC'                THEN 0
                        WHEN 'INTERNAL'              THEN 1
                        WHEN 'CONFIDENTIAL'          THEN 2
                        WHEN 'STRICTLY_CONFIDENTIAL' THEN 3
                        ELSE 99
                      END <= ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(normalizedQuery);
        params.add(tenantId.toString());
        params.add(normalizedQuery);
        params.add(maxLevelOrdinal);

        if (documentTypeFilter != null) {
            sql.append("  AND ci.document_type = ?\n");
            params.add(documentTypeFilter);
        }

        sql.append("ORDER BY bm25_score DESC\nLIMIT ?");
        params.add(limit);

        log.debug("BM25 search tenantId={} query='{}' docType={} limit={}",
                tenantId, normalizedQuery, documentTypeFilter, limit);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
                new BM25SearchResult(
                        rs.getString("chunk_id"),
                        rs.getString("document_id"),
                        rs.getString("tenant_id"),
                        rs.getString("document_type"),
                        rs.getString("classification_level"),
                        rs.getInt("chunk_index"),
                        rs.getString("section_title"),
                        rs.getObject("page_number") != null ? rs.getInt("page_number") : null,
                        rs.getString("chunk_text"),
                        rs.getString("source_object_key"),
                        rs.getDouble("bm25_score")));
    }
}
