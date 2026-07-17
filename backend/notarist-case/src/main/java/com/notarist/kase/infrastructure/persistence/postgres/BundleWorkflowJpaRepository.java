package com.notarist.kase.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleWorkflowJpaRepository extends JpaRepository<BundleWorkflowJpaEntity, String> {
}
