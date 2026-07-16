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
     */
    @Query("""
            SELECT c FROM CaseJpaEntity c
            WHERE c.tenantId = :tenantId
              AND (:state IS NULL OR c.state = :state)
              AND (:caseType IS NULL OR c.caseType = :caseType)
              AND (:assignedNotarisId IS NULL OR c.assignedNotarisId = :assignedNotarisId)
              AND (:createdBy IS NULL OR c.createdBy = :createdBy)
              AND (:createdFrom IS NULL OR c.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR c.createdAt <= :createdTo)
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

    @Query("""
            SELECT COUNT(c) FROM CaseJpaEntity c
            WHERE c.tenantId = :tenantId
              AND (:state IS NULL OR c.state = :state)
              AND (:caseType IS NULL OR c.caseType = :caseType)
              AND (:assignedNotarisId IS NULL OR c.assignedNotarisId = :assignedNotarisId)
              AND (:createdBy IS NULL OR c.createdBy = :createdBy)
              AND (:createdFrom IS NULL OR c.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR c.createdAt <= :createdTo)
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
