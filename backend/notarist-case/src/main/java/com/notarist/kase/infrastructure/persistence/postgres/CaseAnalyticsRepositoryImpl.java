package com.notarist.kase.infrastructure.persistence.postgres;

import com.notarist.kase.application.port.out.CaseAnalyticsRepository;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate analytics queries, tenant-isolated by RLS. All reads are {@code @Transactional(readOnly)}
 * so the {@code notarist_set_identity()} call and the query share one connection/transaction — the
 * setting the row-level-security policy reads back. Each method issues exactly one SQL statement.
 */
@Repository
@Transactional(readOnly = true)
public class CaseAnalyticsRepositoryImpl implements CaseAnalyticsRepository {

    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public CaseAnalyticsRepositoryImpl(RlsContextApplier rlsContextApplier) {
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<CaseState, Long> countByState(UUID tenantId) {
        rlsContextApplier.applyIfPresent(entityManager);
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT c.state, COUNT(c) FROM CaseJpaEntity c "
                                + "WHERE c.tenantId = :tenantId GROUP BY c.state")
                .setParameter("tenantId", tenantId.toString())
                .getResultList();
        Map<CaseState, Long> result = new EnumMap<>(CaseState.class);
        for (Object[] row : rows) {
            result.put(CaseState.valueOf((String) row[0]), asLong(row[1]));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<CaseType, Long> countByType(UUID tenantId) {
        rlsContextApplier.applyIfPresent(entityManager);
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT c.caseType, COUNT(c) FROM CaseJpaEntity c "
                                + "WHERE c.tenantId = :tenantId GROUP BY c.caseType")
                .setParameter("tenantId", tenantId.toString())
                .getResultList();
        Map<CaseType, Long> result = new EnumMap<>(CaseType.class);
        for (Object[] row : rows) {
            result.put(CaseType.valueOf((String) row[0]), asLong(row[1]));
        }
        return result;
    }

    @Override
    public WindowCounts windowCounts(UUID tenantId, Instant dayStart, Instant weekStart, Instant monthStart) {
        rlsContextApplier.applyIfPresent(entityManager);
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT COUNT(*)                                                    AS total,
                               COUNT(*) FILTER (WHERE created_at >= :dayStart)             AS today,
                               COUNT(*) FILTER (WHERE created_at >= :weekStart)            AS this_week,
                               COUNT(*) FILTER (WHERE created_at >= :monthStart)           AS this_month,
                               AVG(EXTRACT(EPOCH FROM (closed_at - created_at)))
                                   FILTER (WHERE closed_at IS NOT NULL)                    AS avg_secs
                        FROM notarial_case
                        WHERE tenant_id = :tenantId
                        """)
                .setParameter("tenantId", tenantId.toString())
                .setParameter("dayStart", Timestamp.from(dayStart))
                .setParameter("weekStart", Timestamp.from(weekStart))
                .setParameter("monthStart", Timestamp.from(monthStart))
                .getSingleResult();

        Double avg = row[4] == null ? null : ((Number) row[4]).doubleValue();
        return new WindowCounts(asLong(row[0]), asLong(row[1]), asLong(row[2]), asLong(row[3]), avg);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MonthlyCount> monthlyTrend(UUID tenantId, Instant since) {
        rlsContextApplier.applyIfPresent(entityManager);
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT CAST(EXTRACT(YEAR  FROM created_at) AS INTEGER) AS yr,
                               CAST(EXTRACT(MONTH FROM created_at) AS INTEGER) AS mon,
                               COUNT(*)                                        AS cnt
                        FROM notarial_case
                        WHERE tenant_id = :tenantId AND created_at >= :since
                        GROUP BY yr, mon
                        ORDER BY yr, mon
                        """)
                .setParameter("tenantId", tenantId.toString())
                .setParameter("since", Timestamp.from(since))
                .getResultList();

        return rows.stream()
                .map(r -> new MonthlyCount(((Number) r[0]).intValue(),
                        ((Number) r[1]).intValue(), asLong(r[2])))
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReminderCandidate> reminderCandidates(UUID tenantId) {
        rlsContextApplier.applyIfPresent(entityManager);
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT case_id, case_number, case_type, state, created_at
                        FROM notarial_case
                        WHERE tenant_id = :tenantId
                          AND state NOT IN ('ARCHIVED', 'CANCELLED')
                          AND (case_type IN ('SKMHT', 'APHT')
                               OR state IN ('WAITING_VERIFICATION', 'WAITING_QC', 'WAITING_NOTARY_APPROVAL'))
                        """)
                .setParameter("tenantId", tenantId.toString())
                .getResultList();

        return rows.stream()
                .map(r -> new ReminderCandidate(
                        UUID.fromString((String) r[0]),
                        (String) r[1],
                        CaseType.valueOf((String) r[2]),
                        CaseState.valueOf((String) r[3]),
                        asInstant(r[4])))
                .toList();
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    /** timestamptz comes back as Timestamp, OffsetDateTime or Instant depending on the driver path. */
    private static Instant asInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }
}
