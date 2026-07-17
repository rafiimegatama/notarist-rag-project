package com.notarist.kase.infrastructure.persistence.postgres;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.kase.application.port.out.BundleRepository;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.infrastructure.persistence.mapper.BundleMapper;
import com.notarist.kase.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@link Bundle} composition persistence, tenant-isolated by PostgreSQL RLS (reusing the case
 * module's {@link RlsContextApplier}). Save is split insert/update so the {@code @Version} column
 * enforces optimistic locking on updates rather than a blind overwrite.
 */
@Repository
@Transactional
public class BundleRepositoryImpl implements BundleRepository {

    private final BundleJpaRepository jpaRepository;
    private final BundleMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public BundleRepositoryImpl(BundleJpaRepository jpaRepository, BundleMapper mapper,
                                RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(Bundle bundle) {
        rlsContextApplier.applyIfPresent(entityManager);
        String id = bundle.bundleId().value().toString();
        Optional<BundleJpaEntity> existing = jpaRepository.findById(id);
        if (existing.isPresent()) {
            BundleJpaEntity managed = existing.get();
            mapper.copyMutableState(bundle, managed);
            jpaRepository.save(managed);
        } else {
            jpaRepository.save(mapper.toNewEntity(bundle));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Bundle> findById(BundleId bundleId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(bundleId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bundle> findByCase(CaseId caseId) {
        rlsContextApplier.applyIfPresent(entityManager);
        // RLS scopes to the caller's tenant; this query scopes to the case.
        return jpaRepository.findByCaseIdOrderByCreatedAtAsc(caseId.value().toString()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Bundle> findByDocumentId(DocumentId documentId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByDocumentId(documentId.value().toString()).stream()
                .findFirst()
                .map(mapper::toDomain);
    }
}
