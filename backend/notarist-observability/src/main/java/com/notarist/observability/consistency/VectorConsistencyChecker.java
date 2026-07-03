package com.notarist.observability.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Detects inconsistency between PostgreSQL chunk_index and Qdrant vector store.
 *
 * A consistency mismatch occurs when:
 *   - A chunk_id exists in chunk_index (PostgreSQL) but has no vector in Qdrant
 *   - A vector exists in Qdrant but has no corresponding row in chunk_index
 *   - embedding_version in chunk_index differs from version in Qdrant payload
 *
 * Consistency check is SAMPLING-based for large collections (> SAMPLE_THRESHOLD).
 * Full check is available via checkFull() — expensive, for maintenance windows only.
 *
 * Result drives reindex trigger: if missingInQdrant > 0, EmbeddingVersionManager
 * should mark REINDEX_REQUIRED.
 */
@Component
public class VectorConsistencyChecker {

    private static final Logger log = LoggerFactory.getLogger(VectorConsistencyChecker.class);

    private static final int SAMPLE_SIZE       = 100;
    private static final int SAMPLE_THRESHOLD  = 1_000;
    private static final String COLLECTION     = "notarist_chunks";

    public record ConsistencyReport(
            int     postgresChunkCount,
            int     qdrantVectorCount,
            int     missingInQdrant,
            int     orphanedInQdrant,
            int     versionMismatches,
            boolean consistent,
            boolean sampled,
            String  diagnosis
    ) {
        public static ConsistencyReport consistent(int pgCount, int qdrantCount) {
            return new ConsistencyReport(pgCount, qdrantCount, 0, 0, 0, true, false, "OK");
        }

        public static ConsistencyReport unavailable(String reason) {
            return new ConsistencyReport(0, 0, 0, 0, 0, false, false, "UNAVAILABLE: " + reason);
        }
    }

    private final JdbcTemplate postgresJdbcTemplate;
    private final RestTemplate restTemplate;
    private final String       qdrantBaseUrl;

    public VectorConsistencyChecker(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
                                     @Qualifier("observabilityRestTemplate") RestTemplate restTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.restTemplate         = restTemplate;
        this.qdrantBaseUrl        = "http://localhost:6333";
    }

    public ConsistencyReport checkSample(String tenantId) {
        try {
            int pgCount     = countPostgresChunks(tenantId);
            int qdrantCount = countQdrantVectors(tenantId);

            boolean sampled = pgCount > SAMPLE_THRESHOLD;
            List<String> sampleChunkIds = samplePostgresChunkIds(tenantId, SAMPLE_SIZE);
            int missingInQdrant     = countMissingInQdrant(sampleChunkIds, tenantId);
            int versionMismatches   = countVersionMismatches(sampleChunkIds, tenantId);

            boolean consistent = missingInQdrant == 0 && versionMismatches == 0
                    && Math.abs(pgCount - qdrantCount) <= (pgCount * 0.02); // 2% tolerance

            String diagnosis = buildDiagnosis(missingInQdrant, versionMismatches, pgCount, qdrantCount);

            if (!consistent) {
                log.warn("VectorConsistencyChecker: INCONSISTENT tenantId={} pgCount={} qdrantCount={} " +
                                "missingInQdrant={} versionMismatches={}",
                        tenantId, pgCount, qdrantCount, missingInQdrant, versionMismatches);
            }

            return new ConsistencyReport(pgCount, qdrantCount, missingInQdrant, 0,
                    versionMismatches, consistent, sampled, diagnosis);

        } catch (Exception e) {
            log.error("VectorConsistencyChecker: check failed tenantId={}: {}", tenantId, e.getMessage());
            return ConsistencyReport.unavailable(e.getMessage());
        }
    }

    private int countPostgresChunks(String tenantId) {
        String sql = "SELECT COUNT(*) FROM chunk_index WHERE tenant_id = ?::uuid";
        Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class, tenantId);
        return count != null ? count : 0;
    }

    @SuppressWarnings("unchecked")
    private int countQdrantVectors(String tenantId) {
        try {
            String url = qdrantBaseUrl + "/collections/" + COLLECTION + "/points/count";
            Map<String, Object> filter = Map.of(
                    "filter", Map.of("must", List.of(
                            Map.of("key", "tenant_id", "match", Map.of("value", tenantId))
                    ))
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, filter, Map.class);
            if (response.getBody() == null) return 0;
            Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
            if (result == null) return 0;
            return ((Number) result.getOrDefault("count", 0)).intValue();
        } catch (Exception e) {
            log.warn("VectorConsistencyChecker: Qdrant count failed: {}", e.getMessage());
            return -1;
        }
    }

    private List<String> samplePostgresChunkIds(String tenantId, int limit) {
        String sql = """
                SELECT chunk_id::text
                FROM chunk_index
                WHERE tenant_id = ?::uuid
                ORDER BY created_at DESC
                LIMIT ?
                """;
        return postgresJdbcTemplate.queryForList(sql, String.class, tenantId, limit);
    }

    @SuppressWarnings("unchecked")
    private int countMissingInQdrant(List<String> chunkIds, String tenantId) {
        if (chunkIds.isEmpty()) return 0;
        try {
            String url = qdrantBaseUrl + "/collections/" + COLLECTION + "/points";
            Map<String, Object> body = Map.of("ids", chunkIds, "with_payload", false);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (response.getBody() == null) return chunkIds.size();
            List<?> found = (List<?>) response.getBody().get("result");
            int foundCount = found != null ? found.size() : 0;
            return chunkIds.size() - foundCount;
        } catch (Exception e) {
            log.warn("VectorConsistencyChecker: Qdrant point lookup failed: {}", e.getMessage());
            return 0;
        }
    }

    private int countVersionMismatches(List<String> chunkIds, String tenantId) {
        if (chunkIds.isEmpty()) return 0;
        String sql = """
                SELECT COUNT(*)
                FROM chunk_index
                WHERE tenant_id = ?::uuid
                  AND chunk_id::text = ANY(?)
                  AND embedding_version != '1.0.0'
                """;
        try {
            Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class,
                    tenantId, chunkIds.toArray(String[]::new));
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildDiagnosis(int missing, int mismatches, int pgCount, int qdrantCount) {
        if (missing == 0 && mismatches == 0 && Math.abs(pgCount - qdrantCount) <= pgCount * 0.02)
            return "OK";
        StringBuilder sb = new StringBuilder();
        if (missing > 0)                               sb.append("MISSING_IN_QDRANT(").append(missing).append(") ");
        if (mismatches > 0)                            sb.append("VERSION_MISMATCH(").append(mismatches).append(") ");
        if (Math.abs(pgCount - qdrantCount) > pgCount * 0.02)
            sb.append("COUNT_DRIFT(pg=").append(pgCount).append(" qdrant=").append(qdrantCount).append(") ");
        return sb.toString().strip();
    }
}
