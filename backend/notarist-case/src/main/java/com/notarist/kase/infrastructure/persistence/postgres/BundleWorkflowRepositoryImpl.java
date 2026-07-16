package com.notarist.kase.infrastructure.persistence.postgres;

import com.notarist.kase.application.port.out.BundleWorkflowRepository;
import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.infrastructure.persistence.mapper.BundleWorkflowMapper;
import com.notarist.kase.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link BundleWorkflow} persistence. Split save so the {@code @Version} column enforces optimistic
 * locking — two concurrent status changes on the same bundle cannot both win.
 */
@Repository
@Transactional
public class BundleWorkflowRepositoryImpl implements BundleWorkflowRepository {

    private final BundleWorkflowJpaRepository jpaRepository;
    private final BundleWorkflowMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public BundleWorkflowRepositoryImpl(BundleWorkflowJpaRepository jpaRepository,
                                        BundleWorkflowMapper mapper,
                                        RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(BundleWorkflow workflow) {
        rlsContextApplier.applyIfPresent(entityManager);
        String id = workflow.bundleId().value().toString();
        Optional<BundleWorkflowJpaEntity> existing = jpaRepository.findById(id);
        if (existing.isPresent()) {
            existing.get().setStatus(workflow.status().name());
            jpaRepository.save(existing.get());
        } else {
            jpaRepository.save(mapper.toNewEntity(workflow));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BundleWorkflow> findById(BundleId bundleId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(bundleId.value().toString()).map(mapper::toDomain);
    }
}
