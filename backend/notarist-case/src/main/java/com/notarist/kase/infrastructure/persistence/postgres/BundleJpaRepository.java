package com.notarist.kase.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BundleJpaRepository extends JpaRepository<BundleJpaEntity, String> {

    /** Case-scoped; RLS scopes the tenant, so no tenant predicate is needed here. */
    List<BundleJpaEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);
}
