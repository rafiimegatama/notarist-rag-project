package com.notarist.kase.application.port.out;

import com.notarist.kase.application.query.CaseFilter;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseNumber;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the Case aggregate.
 *
 * <p>Implemented by {@code CaseRepositoryImpl} (this sprint's vertical slice). The paged
 * {@link #search}/{@link #count} pair backs the list endpoint; the finder methods back the
 * single-case and queue reads. All reads are tenant-scoped by PostgreSQL row-level security in
 * addition to the explicit tenant argument.
 */
public interface CaseRepository {

    void save(Case aCase);

    Optional<Case> findById(CaseId caseId);

    Optional<Case> findByCaseNumber(UUID tenantId, CaseNumber caseNumber);

    List<Case> findByState(UUID tenantId, CaseState state);

    /** The notary's queue. */
    List<Case> findByAssignedNotaris(UUID tenantId, UUID notarisId);

    /** Paged, filtered list for the tenant, newest first. */
    List<Case> search(UUID tenantId, CaseFilter filter, int page, int size);

    /** Total rows matching {@code filter} for the tenant — for the page envelope. */
    long count(UUID tenantId, CaseFilter filter);
}
