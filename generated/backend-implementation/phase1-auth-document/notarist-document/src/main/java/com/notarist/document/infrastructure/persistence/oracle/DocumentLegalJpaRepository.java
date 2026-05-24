package com.notarist.document.infrastructure.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentLegalJpaRepository extends JpaRepository<DocumentLegalJpaEntity, String> {

    Optional<DocumentLegalJpaEntity> findByChecksumSha256AndTenantId(
            String checksumSha256, String tenantId);

    @Query("""
            SELECT d FROM DocumentLegalJpaEntity d
            WHERE d.tenantId = :tenantId
              AND (:documentType IS NULL OR d.documentType = :documentType)
              AND (:status IS NULL OR d.status = :status)
              AND (:maxClearanceLevel IS NULL OR d.classificationLevel IN :allowedLevels)
            ORDER BY d.createdAt DESC
            """)
    List<DocumentLegalJpaEntity> findByTenantIdWithFilters(
            @Param("tenantId") String tenantId,
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("maxClearanceLevel") Integer maxClearanceLevel,
            @Param("allowedLevels") List<String> allowedLevels,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(d) FROM DocumentLegalJpaEntity d
            WHERE d.tenantId = :tenantId
              AND (:documentType IS NULL OR d.documentType = :documentType)
              AND (:status IS NULL OR d.status = :status)
              AND (:maxClearanceLevel IS NULL OR d.classificationLevel IN :allowedLevels)
            """)
    long countByTenantIdWithFilters(
            @Param("tenantId") String tenantId,
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("maxClearanceLevel") Integer maxClearanceLevel,
            @Param("allowedLevels") List<String> allowedLevels);
}
