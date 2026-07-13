package com.notarist.auth.infrastructure.security;

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
 * Applies Oracle VPD identity for the current principal before Oracle queries.
 *
 * The NOTARIST_CTX application context is declared "USING NOTARIST.SET_NOTARIST_CTX",
 * so it can only be written by that trusted package (see Liquibase V004). This applier
 * therefore invokes SET_NOTARIST_CTX.set_identity rather than DBMS_SESSION.SET_CONTEXT
 * directly, and registers a transaction synchronization that clears the identity on the
 * SAME connection before it returns to the pool — preventing cross-connection leakage.
 *
 * The caller MUST run inside a transaction (Oracle repository methods are @Transactional),
 * so the set and the subsequent query share one connection and the clear is guaranteed.
 */
@Component("authVpdContextApplier")
public class VpdContextApplier {

    private static final Logger log = LoggerFactory.getLogger(VpdContextApplier.class);

    private static final String SET_IDENTITY_SQL   = "{ call NOTARIST.SET_NOTARIST_CTX.set_identity(?, ?, ?) }";
    private static final String CLEAR_IDENTITY_SQL = "{ call NOTARIST.SET_NOTARIST_CTX.clear_identity() }";

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                // Without a transaction the SET_CONTEXT call and the query could run on different
                // pooled connections and the context could never be cleared. Skip rather than leak;
                // the application-level tenant filter still applies. Callers should be @Transactional.
                log.warn("VPD identity requested outside an active transaction — skipping to avoid "
                        + "cross-connection leakage; ensure the caller is @Transactional");
                return;
            }

            entityManager.unwrap(Session.class).doWork(connection -> {
                try (CallableStatement cs = connection.prepareCall(SET_IDENTITY_SQL)) {
                    cs.setString(1, principal.userId().toString());
                    cs.setString(2, principal.tenantId().toString());
                    cs.setString(3, principal.highestRole());
                    cs.execute();
                }
            });

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
        });
    }
}
