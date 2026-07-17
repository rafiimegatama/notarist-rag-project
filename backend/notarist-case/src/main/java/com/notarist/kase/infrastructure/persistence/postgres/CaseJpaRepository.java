package com.notarist.kase.infrastructure.persistence.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CaseJpaRepository extends JpaRepository<CaseJpaEntity, String> {

    Optional<CaseJpaEntity> findByTenantIdAndCaseNumber(String tenantId, String caseNumber);

    List<CaseJpaEntity> findByTenantIdAndState(String tenantId, String state);

    List<CaseJpaEntity> findByTenantIdAndAssignedNotarisId(String tenantId, String assignedNotarisId);

    /**
     * Paged, filtered case list. Every filter is null-tolerant: a null argument drops that predicate,
     * so the same query serves "all cases for the tenant" and any combination of filters. Ordered
     * newest-first, matching the document list convention.
     *
     * <p>The two date bounds are compared as {@code CAST(:p AS timestamp)} and the reason is not
     * cosmetic. PostgreSQL infers a parameter's type from its use, and {@code :createdFrom IS NULL}
     * uses it in no typed position at all, so the driver sent an untyped NULL and the server rejected
     * the statement outright:
     *
     * <pre>ERROR: could not determine data type of parameter $10  (SQLState 42P18)</pre>
     *
     * <p>Because the caller passes null whenever it has no date filter — which is every plain
     * {@code GET /api/v1/cases} — this made the ENTIRE case list a 500, not a degraded filter. The
     * cast gives the parameter a type when it is null; the second use of each is already typed by the
     * comparison against {@code c.createdAt}, so only the IS NULL side needs it. The String params
     * above do not: Hibernate binds those as varchar, which PostgreSQL can already resolve.
     *
     * <p>Not caught before because the module's own tests do not run this query with all-null filters
     * against a real PostgreSQL. Verified against one (Sprint 7).
     */
    @Query("""
            SELECT c FROM CaseJpaEntity c
            WHERE c.tenantId = :tenantId
              AND (:state IS NULL OR c.state = :state)
              AND (:caseType IS NULL OR c.caseType = :caseType)
              AND (:assignedNotarisId IS NULL OR c.assignedNotarisId = :assignedNotarisId)
              AND (:createdBy IS NULL OR c.createdBy = :createdBy)
              AND (CAST(:createdFrom AS timestamp) IS NULL OR c.createdAt >= :createdFrom)
              AND (CAST(:createdTo AS timestamp) IS NULL OR c.createdAt <= :createdTo)
            ORDER BY c.createdAt DESC
            """)
    List<CaseJpaEntity> search(
            @Param("tenantId") String tenantId,
            @Param("state") String state,
            @Param("caseType") String caseType,
            @Param("assignedNotarisId") String assignedNotarisId,
            @Param("createdBy") String createdBy,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    /** Row count for {@link #search}. Same predicates, so the same casts — see {@link #search}. */
    @Query("""
            SELECT COUNT(c) FROM CaseJpaEntity c
            WHERE c.tenantId = :tenantId
              AND (:state IS NULL OR c.state = :state)
              AND (:caseType IS NULL OR c.caseType = :caseType)
              AND (:assignedNotarisId IS NULL OR c.assignedNotarisId = :assignedNotarisId)
              AND (:createdBy IS NULL OR c.createdBy = :createdBy)
              AND (CAST(:createdFrom AS timestamp) IS NULL OR c.createdAt >= :createdFrom)
              AND (CAST(:createdTo AS timestamp) IS NULL OR c.createdAt <= :createdTo)
            """)
    long countBySearch(
            @Param("tenantId") String tenantId,
            @Param("state") String state,
            @Param("caseType") String caseType,
            @Param("assignedNotarisId") String assignedNotarisId,
            @Param("createdBy") String createdBy,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo);
}
