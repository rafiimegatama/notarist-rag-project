package com.notarist.kase.infrastructure.persistence.postgres;

import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.query.CaseFilter;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseNumber;
import com.notarist.kase.infrastructure.persistence.mapper.CaseMapper;
import com.notarist.kase.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Case persistence, tenant-isolated by PostgreSQL RLS.
 *
 * <p>The tenant identity is applied at the start of each method; {@code @Transactional} guarantees
 * the {@code notarist_set_identity()} call and the JPA work share one connection and transaction,
 * which is what makes the transaction-local setting visible to the row-level-security policy.
 *
 * <p><b>Save is deliberately split.</b> For a new case we persist a fresh entity (version 0). For an
 * existing one we load the MANAGED entity and copy the aggregate's mutable state onto it, so
 * Hibernate's dirty checking and the {@code @Version} column enforce optimistic locking — two
 * concurrent status changes cannot both win. A blind {@code save(mapper.toNewEntity(...))} on an
 * update would reset the version and silently clobber a concurrent writer.
 */
@Repository
@Transactional
public class CaseRepositoryImpl implements CaseRepository {

    private final CaseJpaRepository jpaRepository;
    private final CaseMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public CaseRepositoryImpl(CaseJpaRepository jpaRepository, CaseMapper mapper,
                              RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(Case aCase) {
        rlsContextApplier.applyIfPresent(entityManager);
        String id = aCase.caseId().value().toString();
        Optional<CaseJpaEntity> existing = jpaRepository.findById(id);
        if (existing.isPresent()) {
            CaseJpaEntity managed = existing.get();
            mapper.copyMutableState(aCase, managed);
            jpaRepository.save(managed);        // managed → dirty check + @Version bump
        } else {
            jpaRepository.save(mapper.toNewEntity(aCase));   // fresh row, version 0
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Case> findById(CaseId caseId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(caseId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Case> findByCaseNumber(UUID tenantId, CaseNumber caseNumber) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByTenantIdAndCaseNumber(tenantId.toString(), caseNumber.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Case> findByState(UUID tenantId, CaseState state) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByTenantIdAndState(tenantId.toString(), state.name())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Case> findByAssignedNotaris(UUID tenantId, UUID notarisId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByTenantIdAndAssignedNotarisId(tenantId.toString(), notarisId.toString())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Case> search(UUID tenantId, CaseFilter filter, int page, int size) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.search(
                        tenantId.toString(),
                        filter.state() != null ? filter.state().name() : null,
                        filter.caseType() != null ? filter.caseType().name() : null,
                        filter.assignedNotarisId() != null ? filter.assignedNotarisId().toString() : null,
                        filter.createdBy() != null ? filter.createdBy().toString() : null,
                        filter.createdFrom(),
                        filter.createdTo(),
                        PageRequest.of(page, size))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count(UUID tenantId, CaseFilter filter) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.countBySearch(
                tenantId.toString(),
                filter.state() != null ? filter.state().name() : null,
                filter.caseType() != null ? filter.caseType().name() : null,
                filter.assignedNotarisId() != null ? filter.assignedNotarisId().toString() : null,
                filter.createdBy() != null ? filter.createdBy().toString() : null,
                filter.createdFrom(),
                filter.createdTo());
    }
}
