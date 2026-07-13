package com.notarist.ingest.infrastructure.persistence.postgres;

import com.notarist.core.domain.policy.OcrReviewStatus;
import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.ingest.application.port.out.ChunkMetadataRepository;
import com.notarist.ingest.domain.model.ChunkMetadata;
import com.notarist.ingest.domain.model.IngestionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL chunk_index persistence. Same datasource/template as the
 * ingestion queue (ingestJdbcTemplate) so pipeline writes share one pool.
 *
 * Embeddings are stored as REAL[] (float4[]) — 1024 elements per chunk.
 */
@Repository
public class ChunkMetadataRepositoryImpl implements ChunkMetadataRepository {

    private static final Logger log = LoggerFactory.getLogger(ChunkMetadataRepositoryImpl.class);

    private static final String SQL_DELETE_FOR_INGESTION = """
            DELETE FROM chunk_index WHERE ingestion_id = ?
            """;

    private static final String SQL_INSERT = """
            INSERT INTO chunk_index
                (chunk_id, ingestion_id, document_id, tenant_id, document_type,
                 classification_level, chunk_index, section_title, page_number,
                 chunk_text, source_object_key, token_count, start_offset,
                 end_offset, chunk_strategy, overlap_tokens, pasal_ref,
                 ocr_confidence, review_status, searchable, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_SELECT_BASE = """
            SELECT chunk_id, ingestion_id, document_id, tenant_id, document_type,
                   classification_level, chunk_index, section_title, page_number,
                   chunk_text, source_object_key, token_count, start_offset,
                   end_offset, chunk_strategy, overlap_tokens, pasal_ref,
                   ocr_confidence, review_status, searchable,
                   embedding, embedding_model, embedded_at, created_at
            FROM chunk_index
            WHERE ingestion_id = ?
            """;

    private static final String SQL_SELECT_BY_INGESTION =
            SQL_SELECT_BASE + " ORDER BY chunk_index";

    private static final String SQL_SELECT_UNEMBEDDED =
            SQL_SELECT_BASE + " AND embedding IS NULL ORDER BY chunk_index";

    private static final String SQL_SELECT_EMBEDDED =
            SQL_SELECT_BASE + " AND embedding IS NOT NULL ORDER BY chunk_index";

    private static final String SQL_SAVE_EMBEDDING = """
            UPDATE chunk_index
            SET embedding = ?, embedding_model = ?, embedded_at = ?
            WHERE chunk_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ChunkMetadataRepositoryImpl(
            @Qualifier("ingestJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replaceForIngestion(IngestionId ingestionId, List<ChunkMetadata> chunks) {
        int deleted = jdbcTemplate.update(SQL_DELETE_FOR_INGESTION, ingestionId.value().toString());
        if (deleted > 0) {
            log.info("Replaced {} existing chunk rows for ingestionId={}", deleted, ingestionId);
        }

        jdbcTemplate.batchUpdate(SQL_INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ChunkMetadata c = chunks.get(i);
                ps.setString(1, c.chunkId().value().toString());
                ps.setString(2, c.ingestionId().value().toString());
                ps.setString(3, c.documentId().value().toString());
                ps.setObject(4, c.tenantId());
                ps.setString(5, c.documentType().name());
                ps.setString(6, c.classificationLevel().name());
                ps.setInt(7, c.chunkIndex());
                ps.setString(8, c.sectionTitle());
                if (c.pageNumber() != null) ps.setInt(9, c.pageNumber());
                else ps.setNull(9, Types.INTEGER);
                ps.setString(10, c.chunkText());
                ps.setString(11, c.sourceObjectKey());
                ps.setInt(12, c.tokenCount());
                ps.setInt(13, c.startOffset());
                ps.setInt(14, c.endOffset());
                ps.setString(15, c.chunkStrategy());
                ps.setInt(16, c.overlapTokens());
                ps.setString(17, c.pasalRef());
                ps.setFloat(18, c.ocrConfidence());
                ps.setString(19, c.reviewStatus().name());
                ps.setBoolean(20, c.searchable());
                ps.setTimestamp(21, Timestamp.from(c.createdAt()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
        log.info("Persisted {} chunks for ingestionId={}", chunks.size(), ingestionId);
    }

    @Override
    public List<ChunkMetadata> findByIngestionId(IngestionId ingestionId) {
        return jdbcTemplate.query(SQL_SELECT_BY_INGESTION, this::mapRow,
                ingestionId.value().toString());
    }

    @Override
    public List<ChunkMetadata> findUnembedded(IngestionId ingestionId) {
        return jdbcTemplate.query(SQL_SELECT_UNEMBEDDED, this::mapRow,
                ingestionId.value().toString());
    }

    @Override
    public List<ChunkMetadata> findEmbedded(IngestionId ingestionId) {
        return jdbcTemplate.query(SQL_SELECT_EMBEDDED, this::mapRow,
                ingestionId.value().toString());
    }

    @Override
    public void saveEmbeddings(List<ChunkMetadata> embeddedChunks) {
        jdbcTemplate.batchUpdate(SQL_SAVE_EMBEDDING, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ChunkMetadata c = embeddedChunks.get(i);
                if (c.embedding() == null) {
                    throw new SQLException("saveEmbeddings called with null embedding for chunkId="
                            + c.chunkId().value());
                }
                ps.setArray(1, toSqlArray(ps, c.embedding()));
                ps.setString(2, c.embeddingModel());
                ps.setTimestamp(3, c.embeddedAt() != null
                        ? Timestamp.from(c.embeddedAt()) : null);
                ps.setString(4, c.chunkId().value().toString());
            }

            @Override
            public int getBatchSize() {
                return embeddedChunks.size();
            }
        });
        log.info("Persisted embeddings for {} chunks", embeddedChunks.size());
    }

    private static Array toSqlArray(PreparedStatement ps, float[] vector) throws SQLException {
        Float[] boxed = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) boxed[i] = vector[i];
        return ps.getConnection().createArrayOf("float4", boxed);
    }

    private ChunkMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChunkMetadata(
                new ChunkId(UUID.fromString(rs.getString("chunk_id"))),
                IngestionId.of(UUID.fromString(rs.getString("ingestion_id"))),
                new DocumentId(UUID.fromString(rs.getString("document_id"))),
                rs.getObject("tenant_id", UUID.class),
                JenisDokumen.valueOf(rs.getString("document_type")),
                ClassificationLevel.valueOf(rs.getString("classification_level")),
                rs.getInt("chunk_index"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getInt("token_count"),
                rs.getString("chunk_strategy"),
                rs.getInt("overlap_tokens"),
                rs.getString("section_title"),
                rs.getString("pasal_ref"),
                rs.getObject("page_number") != null ? rs.getInt("page_number") : null,
                rs.getString("chunk_text"),
                rs.getString("source_object_key"),
                rs.getFloat("ocr_confidence"),
                OcrReviewStatus.valueOf(rs.getString("review_status")),
                rs.getBoolean("searchable"),
                readEmbedding(rs),
                rs.getString("embedding_model"),
                rs.getTimestamp("embedded_at") != null
                        ? rs.getTimestamp("embedded_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant());
    }

    private static float[] readEmbedding(ResultSet rs) throws SQLException {
        Array array = rs.getArray("embedding");
        if (array == null) return null;
        Float[] boxed = (Float[]) array.getArray();
        float[] vector = new float[boxed.length];
        for (int i = 0; i < boxed.length; i++) vector[i] = boxed[i];
        return vector;
    }
}
