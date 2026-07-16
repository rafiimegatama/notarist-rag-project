package com.notarist.verification.application.port.out;

import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.VerificationId;

import java.util.Optional;

/**
 * Persistence port for the {@link Verification} aggregate. Tenant isolation is enforced by PostgreSQL
 * row-level security in the adapter, not here.
 */
public interface VerificationRepository {

    /** Inserts a new verification, or updates an existing one (optimistic-locked) and appends its new audit rows. */
    void save(Verification verification);

    Optional<Verification> findByBundleId(BundleId bundleId);

    Optional<Verification> findById(VerificationId verificationId);

    boolean existsByBundleId(BundleId bundleId);
}
