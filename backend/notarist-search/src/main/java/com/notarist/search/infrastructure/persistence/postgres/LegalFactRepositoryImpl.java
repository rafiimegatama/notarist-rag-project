package com.notarist.search.infrastructure.persistence.postgres;

import com.notarist.search.application.port.out.LegalFactPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic facts from PostgreSQL. This is the engine behind every factual and status answer.
 *
 * <p>Conventions follow the existing repositories: explicit column lists (never {@code SELECT *}),
 * bound parameters only, and an explicit {@code tenant_id} predicate in addition to Postgres RLS —
 * defence in depth, since RLS currently covers only three tables.
 *
 * <p><b>What it can and cannot answer.</b> It reads {@code dokumen_legal} and {@code ingestion_job},
 * which is enough for document counts, ingestion failures, and per-type breakdowns. It cannot answer
 * anything about cases, bundles, approvals or statutory deadlines, because those tables do not exist
 * yet. Those methods return {@link FactAvailability#NOT_IMPLEMENTED} rather than a plausible zero —
 * "0 bundles delivered" would be a false statement, not a missing one.
 */
@Repository
public class LegalFactRepositoryImpl implements LegalFactPort {

    private static final Logger log = LoggerFactory.getLogger(LegalFactRepositoryImpl.class);

    /** Ingestion statuses that mean the document did not make it through the pipeline. */
    private static final String FAILED_STATUSES = "('FAILED', 'DLQ')";

    private final JdbcTemplate jdbcTemplate;

    public LegalFactRepositoryImpl(@Qualifier("postgresJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CountFact countDocuments(UUID tenantId, TimeWindow window, String jenisAkta, String documentType) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM dokumen_legal dl WHERE dl.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId.toString());

        appendTimeWindow(sql, args, window, "dl.created_at");

        if (jenisAkta != null) {
            sql.append(" AND dl.jenis_akta = ?");
            args.add(jenisAkta);
        }
        if (documentType != null) {
            sql.append(" AND dl.document_type = ?");
            args.add(documentType);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        log.debug("countDocuments tenant={} window={} jenisAkta={} → {}", tenantId, window, jenisAkta, count);
        return CountFact.of(count == null ? 0L : count);
    }

    @Override
    public CountFact countFailedDocuments(UUID tenantId, TimeWindow window) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM dokumen_legal dl "
                        + "WHERE dl.tenant_id = ? AND dl.status IN " + FAILED_STATUSES);
        List<Object> args = new ArrayList<>();
        args.add(tenantId.toString());

        appendTimeWindow(sql, args, window, "dl.created_at");

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return CountFact.of(count == null ? 0L : count);
    }

    @Override
    public List<GroupCount> countGrouped(UUID tenantId, TimeWindow window, GroupBy groupBy) {
        // Column is chosen from a closed enum — never interpolated from user input.
        String column = switch (groupBy) {
            case JENIS_AKTA    -> "dl.jenis_akta";
            case STATUS        -> "dl.status";
            case DOCUMENT_TYPE -> "dl.document_type";
        };

        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(" + column + ", 'TIDAK DIKETAHUI') AS group_key, COUNT(*) AS group_count "
                        + "FROM dokumen_legal dl WHERE dl.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId.toString());

        appendTimeWindow(sql, args, window, "dl.created_at");

        sql.append(" GROUP BY 1 ORDER BY group_count DESC");

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new GroupCount(rs.getString("group_key"), rs.getLong("group_count")),
                args.toArray());
    }

    @Override
    public List<DocumentFact> findByNomorAkta(UUID tenantId, String nomorAkta) {
        String sql = """
                SELECT
                    dl.document_id,
                    dl.document_title,
                    dl.nomor_akta,
                    dl.jenis_akta,
                    dl.document_type,
                    dl.status,
                    dl.classification_level,
                    dl.created_at,
                    dl.indexed_at
                FROM dokumen_legal dl
                WHERE dl.tenant_id = ?
                  AND dl.nomor_akta = ?
                ORDER BY dl.created_at DESC
                LIMIT 20
                """;
        return jdbcTemplate.query(sql, this::mapDocumentFact, tenantId.toString(), nomorAkta);
    }

    @Override
    public List<DocumentFact> findDocumentStatus(UUID tenantId, String nomorAkta) {
        // Exact match first; fall back to a prefix match so "akta 125" finds "125/VII/2024".
        List<DocumentFact> exact = findByNomorAkta(tenantId, nomorAkta);
        if (!exact.isEmpty()) {
            return exact;
        }

        String sql = """
                SELECT
                    dl.document_id,
                    dl.document_title,
                    dl.nomor_akta,
                    dl.jenis_akta,
                    dl.document_type,
                    dl.status,
                    dl.classification_level,
                    dl.created_at,
                    dl.indexed_at
                FROM dokumen_legal dl
                WHERE dl.tenant_id = ?
                  AND dl.nomor_akta LIKE ?
                ORDER BY dl.created_at DESC
                LIMIT 20
                """;
        return jdbcTemplate.query(sql, this::mapDocumentFact, tenantId.toString(), nomorAkta + "%");
    }

    private DocumentFact mapDocumentFact(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DocumentFact(
                rs.getString("document_id"),
                rs.getString("document_title"),
                rs.getString("nomor_akta"),
                rs.getString("jenis_akta"),
                rs.getString("document_type"),
                rs.getString("status"),
                rs.getString("classification_level"),
                rs.getString("created_at"),
                rs.getString("indexed_at"));
    }

    /** Time bounds are expressed against the DB clock, not the JVM's, so results are reproducible. */
    private void appendTimeWindow(StringBuilder sql, List<Object> args, TimeWindow window, String column) {
        switch (window) {
            case TODAY      -> sql.append(" AND ").append(column).append(" >= date_trunc('day', NOW())");
            case THIS_MONTH -> sql.append(" AND ").append(column).append(" >= date_trunc('month', NOW())");
            case THIS_YEAR  -> sql.append(" AND ").append(column).append(" >= date_trunc('year', NOW())");
            case NEXT_WEEK  -> sql.append(" AND ").append(column)
                    .append(" BETWEEN NOW() AND NOW() + INTERVAL '7 days'");
            case ALL        -> { /* no bound */ }
        }
    }
}
