package com.notarist.kase.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BundleJpaRepository extends JpaRepository<BundleJpaEntity, String> {

    /** Case-scoped; RLS scopes the tenant, so no tenant predicate is needed here. */
    List<BundleJpaEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);

    /**
     * Bundles containing the given document, most recent first. Joins the {@code bundle_document}
     * element collection; RLS scopes the tenant, so no tenant predicate is needed here.
     */
    @Query("SELECT b FROM BundleJpaEntity b JOIN b.documents d "
            + "WHERE d.documentId = :documentId ORDER BY b.createdAt DESC")
    List<BundleJpaEntity> findByDocumentId(@Param("documentId") String documentId);
}
