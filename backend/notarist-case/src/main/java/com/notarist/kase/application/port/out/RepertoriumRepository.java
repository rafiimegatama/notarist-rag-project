package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.model.Repertorium;
import com.notarist.kase.domain.valueobject.RepertoriumId;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the statutory register.
 *
 * <p>⚠️ The implementation of {@link #findForUpdate} MUST take a pessimistic lock (SELECT … FOR
 * UPDATE). Optimistic concurrency is not acceptable here: two cases finalizing at the same instant
 * must not receive the same akta number, and a rolled-back transaction must not burn one. A database
 * SEQUENCE is likewise unacceptable — it leaves gaps on rollback, which is exactly what the law
 * forbids. This is the one place in the system that genuinely needs serialization.
 */
public interface RepertoriumRepository {

    void save(Repertorium repertorium);

    Optional<Repertorium> findById(RepertoriumId repertoriumId);

    /** Loads the register for a notary/year under a pessimistic lock, for allocation. */
    Optional<Repertorium> findForUpdate(UUID tenantId, UUID notarisId, int year);
}
