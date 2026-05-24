package com.notarist.document.infrastructure.persistence.oracle;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.document.domain.model.DocumentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * PHASE 6A.2-FIX: filter params updated to use typed enums now that entity
 * fields are @Enumerated(STRING). String params for documentType, status, and
 * allowedLevels replaced with JenisDokumen, DocumentStatus, and
 * List<ClassificationLevel>. maxClearanceLevel changed from Integer to
 * ClassificationLevel (IS NULL sentinel still works with null enum).
 */
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
            @Param("documentType") JenisDokumen documentType,
            @Param("status") DocumentStatus status,
            @Param("maxClearanceLevel") ClassificationLevel maxClearanceLevel,
            @Param("allowedLevels") List<ClassificationLevel> allowedLevels,
            Pageable pageable);

    @Query("""
            SELECT COUNT(d) FROM DocumentLegalJpaEntity d
            WHERE d.tenantId = :tenantId
              AND (:documentType IS NULL OR d.documentType = :documentType)
              AND (:status IS NULL OR d.status = :status)
              AND (:maxClearanceLevel IS NULL OR d.classificationLevel IN :allowedLevels)
            """)
    long countByTenantIdWithFilters(
            @Param("tenantId") String tenantId,
            @Param("documentType") JenisDokumen documentType,
            @Param("status") DocumentStatus status,
            @Param("maxClearanceLevel") ClassificationLevel maxClearanceLevel,
            @Param("allowedLevels") List<ClassificationLevel> allowedLevels);
}
