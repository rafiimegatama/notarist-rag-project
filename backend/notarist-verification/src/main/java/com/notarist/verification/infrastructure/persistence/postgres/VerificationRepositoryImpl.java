package com.notarist.verification.infrastructure.persistence.postgres;

import com.notarist.verification.application.port.out.VerificationRepository;
import com.notarist.verification.domain.model.ItemAuditEntry;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.VerificationId;
import com.notarist.verification.infrastructure.persistence.mapper.VerificationMapper;
import com.notarist.verification.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Verification persistence, tenant-isolated by PostgreSQL RLS.
 *
 * <p>The tenant identity is applied at the start of each method; {@code @Transactional} guarantees the
 * {@code notarist_set_identity()} call and the JPA work share one connection and transaction, which is
 * what makes the transaction-local setting visible to the row-level-security policy.
 *
 * <p><b>Save is deliberately split.</b> A new verification is persisted as a fresh root (version 0)
 * with its children cascaded in. An existing one is loaded MANAGED and the aggregate's mutable state
 * is copied onto it and its managed children, so Hibernate's dirty checking and the {@code @Version}
 * column enforce optimistic locking. New append-only audit rows are inserted separately.
 */
@Repository
@Transactional
public class VerificationRepositoryImpl implements VerificationRepository {

    private final VerificationJpaRepository jpaRepository;
    private final VerificationItemAuditJpaRepository auditJpaRepository;
    private final VerificationMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public VerificationRepositoryImpl(VerificationJpaRepository jpaRepository,
                                      VerificationItemAuditJpaRepository auditJpaRepository,
                                      VerificationMapper mapper,
                                      RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.auditJpaRepository = auditJpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(Verification verification) {
        rlsContextApplier.applyIfPresent(entityManager);
        String id = verification.verificationId().value().toString();
        Optional<VerificationJpaEntity> existing = jpaRepository.findById(id);
        if (existing.isPresent()) {
            VerificationJpaEntity managed = existing.get();
            mapper.copyMutableState(verification, managed);   // managed → dirty check + @Version bump
            jpaRepository.save(managed);
        } else {
            jpaRepository.save(mapper.toNewEntity(verification));   // fresh root + cascaded children
        }
        List<ItemAuditEntry> pending = verification.pullAuditEntries();
        for (ItemAuditEntry entry : pending) {
            auditJpaRepository.save(mapper.toAuditEntity(id, entry));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Verification> findByBundleId(BundleId bundleId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByBundleId(bundleId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Verification> findById(VerificationId verificationId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(verificationId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByBundleId(BundleId bundleId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.existsByBundleId(bundleId.value().toString());
    }
}
