package com.notarist.kase.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BundleTimelineJpaRepository extends JpaRepository<BundleTimelineJpaEntity, String> {

    Optional<BundleTimelineJpaEntity> findByBundleId(String bundleId);
}
