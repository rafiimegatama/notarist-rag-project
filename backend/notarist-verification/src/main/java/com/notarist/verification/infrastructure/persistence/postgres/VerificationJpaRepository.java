package com.notarist.verification.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationJpaRepository extends JpaRepository<VerificationJpaEntity, String> {

    Optional<VerificationJpaEntity> findByBundleId(String bundleId);

    boolean existsByBundleId(String bundleId);
}
