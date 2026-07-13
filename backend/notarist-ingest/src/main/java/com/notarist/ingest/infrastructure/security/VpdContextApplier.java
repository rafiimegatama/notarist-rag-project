package com.notarist.ingest.infrastructure.security;

import com.notarist.core.security.VpdContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.CallableStatement;

/**
 * Ingest module VPD applier — local copy to enforce module isolation (no auth/document import).
 *
 * The NOTARIST_CTX application context is declared "USING NOTARIST.SET_NOTARIST_CTX",
 * so it can only be written by that trusted package (see Liquibase V004). This applier
 * invokes SET_NOTARIST_CTX.set_identity rather than DBMS_SESSION.SET_CONTEXT directly,
 * and registers a transaction synchronization that clears the identity on the SAME
 * connection before it returns to the pool — preventing cross-connection leakage.
 * Callers into Oracle repositories are @Transactional so set, query and clear share one connection.
 */
@Component("ingestVpdContextApplier")
public class VpdContextApplier {

    private static final Logger log = LoggerFactory.getLogger(VpdContextApplier.class);

    private static final String SET_IDENTITY_SQL   = "{ call NOTARIST.SET_NOTARIST_CTX.set_identity(?, ?, ?) }";
    private static final String SET_SYSTEM_SQL     = "{ call NOTARIST.SET_NOTARIST_CTX.set_system_identity() }";
    private static final String CLEAR_IDENTITY_SQL = "{ call NOTARIST.SET_NOTARIST_CTX.clear_identity() }";

    /**
     * Applies the caller's VPD identity, falling back to a trusted system session when there is
     * no principal.
     *
     * <p>The ingest repositories are reached from two directions: an HTTP upload (authenticated —
     * the principal's tenant applies) and the background pipeline workers driven by
     * {@code IngestionQueueScheduler} (no principal, and they legitimately process jobs across
     * every tenant). The tenant policy is fail-closed (Liquibase V005), so without this fallback
     * the workers would silently see zero rows and the whole pipeline would stall.
     *
     * <p>This exemption is confined to the ingest job/queue tables. It is NOT available on
     * DOKUMEN_LEGAL — the document repository has no system path, so the legal content stays
     * strictly tenant-filtered by the database.
     */
    public void applyPrincipalOrSystem(EntityManager entityManager) {
        if (VpdContextHolder.get().isPresent()) {
            applyIfPresent(entityManager);
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("VPD system identity requested outside an active transaction — skipping to "
                    + "avoid cross-connection leakage; ensure the caller is @Transactional");
            return;
        }

        try {
            entityManager.unwrap(Session.class).doWork(connection -> {
                try (CallableStatement cs = connection.prepareCall(SET_SYSTEM_SQL)) {
                    cs.execute();
                }
            });
        } catch (Exception e) {
            log.warn("VPD system identity apply failed: {}", e.getMessage());
            return;
        }

        registerClearOnCompletion(entityManager);
    }

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                log.warn("VPD identity requested outside an active transaction — skipping to avoid "
                        + "cross-connection leakage; ensure the caller is @Transactional");
                return;
            }

            try {
                entityManager.unwrap(Session.class).doWork(connection -> {
                    try (CallableStatement cs = connection.prepareCall(SET_IDENTITY_SQL)) {
                        cs.setString(1, principal.userId().toString());
                        cs.setString(2, principal.tenantId().toString());
                        cs.setString(3, principal.highestRole());
                        cs.execute();
                    }
                });
            } catch (Exception e) {
                log.warn("VPD identity apply failed — continuing without tenant isolation: {}", e.getMessage());
                return;
            }

            registerClearOnCompletion(entityManager);
        });
    }

    /**
     * Clears the identity on the SAME connection before it returns to the pool, so the next
     * borrower cannot inherit the previous tenant — or, worse, a system exemption.
     */
    private void registerClearOnCompletion(EntityManager entityManager) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCompletion() {
                try {
                    entityManager.unwrap(Session.class).doWork(connection -> {
                        try (CallableStatement cs = connection.prepareCall(CLEAR_IDENTITY_SQL)) {
                            cs.execute();
                        }
                    });
                } catch (Exception e) {
                    log.warn("VPD identity clear failed: {}", e.getMessage());
                }
            }
        });
    }
}
