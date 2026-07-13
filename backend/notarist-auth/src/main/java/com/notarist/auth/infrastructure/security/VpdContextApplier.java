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
    private static final String SET_SYSTEM_SQL     = "{ call NOTARIST.SET_NOTARIST_CTX.set_system_identity() }";
    private static final String CLEAR_IDENTITY_SQL = "{ call NOTARIST.SET_NOTARIST_CTX.clear_identity() }";

    /**
     * Marks the session as a trusted system session, exempt from VPD tenant filtering.
     *
     * <p>TENANT_ISOLATION_POLICY (Liquibase V005) is fail-closed: a session with no tenant
     * identity sees no rows. The pre-authentication login lookup has no tenant by definition —
     * it reads the very row that reveals the tenant — so it must opt out of filtering
     * explicitly. This is the only exemption in the auth module; do not add others without
     * understanding that each one is a hole in the F7 backstop.
     */
    public void applySystemIdentity(EntityManager entityManager) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("VPD system identity requested outside an active transaction — skipping to "
                    + "avoid cross-connection leakage; ensure the caller is @Transactional");
            return;
        }

        entityManager.unwrap(Session.class).doWork(connection -> {
            try (CallableStatement cs = connection.prepareCall(SET_SYSTEM_SQL)) {
                cs.execute();
            }
        });

        registerClearOnCompletion(entityManager);
    }

    /**
     * Applies an explicit VPD identity that does NOT come from the authenticated principal.
     *
     * <p>Needed by the refresh-token flow: {@code /api/v1/auth/refresh} is a permitAll endpoint,
     * so no JWT principal exists and {@link VpdContextHolder} is empty — yet the tenant IS known,
     * because it is carried on the validated session row. Establishing the real tenant here keeps
     * the subsequent user lookup database-filtered, instead of reaching for the blunt system
     * exemption. Callers must have already validated the session the identity is derived from.
     */
    public void applyIdentity(EntityManager entityManager, String userId, String tenantId, String role) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("VPD identity requested outside an active transaction — skipping to avoid "
                    + "cross-connection leakage; ensure the caller is @Transactional");
            return;
        }

        entityManager.unwrap(Session.class).doWork(connection -> {
            try (CallableStatement cs = connection.prepareCall(SET_IDENTITY_SQL)) {
                cs.setString(1, userId);
                cs.setString(2, tenantId);
                cs.setString(3, role);
                cs.execute();
            }
        });

        registerClearOnCompletion(entityManager);
    }

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

            registerClearOnCompletion(entityManager);
        });
    }

    /**
     * Clears the identity on the SAME connection before it returns to the pool. Without this a
     * pooled connection keeps the last caller's tenant (or system exemption) and the next
     * borrower inherits it.
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
