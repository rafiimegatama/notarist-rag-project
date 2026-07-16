package com.notarist.auth.infrastructure.security;

import com.notarist.core.security.VpdContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;

/**
 * Establishes the row-level-security identity for the current principal before PostgreSQL queries.
 *
 * <p>Backed by PostgreSQL RLS (Flyway V9), which replaced Oracle VPD. The identity lives in
 * transaction-local session settings ({@code notarist.tenant_id} and friends) written by
 * {@code notarist_set_identity()}; the policies on {@code notarist_user} read them back through
 * {@code current_setting()}. The principal itself still arrives via {@link VpdContextHolder}, whose
 * name is a holdover from the Oracle VPD implementation — it is read by the controllers, so it was
 * left alone; the contract it carries is unchanged.
 *
 * <p>The caller MUST run inside a transaction (repository methods are @Transactional). The settings
 * are written with {@code is_local => true}, so PostgreSQL scopes them to that transaction and
 * discards them at commit or rollback — a pooled connection cannot carry one caller's tenant into
 * the next borrower's query. Outside a transaction the write would be a no-op, which the fail-closed
 * policy would turn into "zero rows", so these methods refuse to run there rather than mislead.
 */
@Component("authRlsContextApplier")
public class RlsContextApplier {

    private static final Logger log = LoggerFactory.getLogger(RlsContextApplier.class);

    private static final String SET_IDENTITY_SQL   = "SELECT notarist_set_identity(?, ?, ?)";
    private static final String SET_SYSTEM_SQL     = "SELECT notarist_set_system_identity()";
    private static final String CLEAR_IDENTITY_SQL = "SELECT notarist_clear_identity()";

    /**
     * Marks the session as a trusted system session, exempt from RLS tenant filtering.
     *
     * <p>The tenant policy (Flyway V9) is fail-closed: a session with no tenant identity sees no
     * rows. The pre-authentication login lookup has no tenant by definition — it reads the very row
     * that reveals the tenant — so it must opt out of filtering explicitly. This is the only
     * exemption in the auth module; do not add others without understanding that each one is a hole
     * in the isolation backstop.
     */
    public void applySystemIdentity(EntityManager entityManager) {
        if (!requireTransaction()) {
            return;
        }

        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SET_SYSTEM_SQL)) {
                ps.execute();
            }
        });

        registerClearOnCompletion(entityManager);
    }

    /**
     * Applies an explicit identity that does NOT come from the authenticated principal.
     *
     * <p>Needed by the refresh-token flow: {@code /api/v1/auth/refresh} is a permitAll endpoint,
     * so no JWT principal exists and {@link VpdContextHolder} is empty — yet the tenant IS known,
     * because it is carried on the validated session row. Establishing the real tenant here keeps
     * the subsequent user lookup database-filtered, instead of reaching for the blunt system
     * exemption. Callers must have already validated the session the identity is derived from.
     */
    public void applyIdentity(EntityManager entityManager, String userId, String tenantId, String role) {
        if (!requireTransaction()) {
            return;
        }

        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SET_IDENTITY_SQL)) {
                ps.setString(1, userId);
                ps.setString(2, tenantId);
                ps.setString(3, role);
                ps.execute();
            }
        });

        registerClearOnCompletion(entityManager);
    }

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            if (!requireTransaction()) {
                return;
            }

            entityManager.unwrap(Session.class).doWork(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SET_IDENTITY_SQL)) {
                    ps.setString(1, principal.userId().toString());
                    ps.setString(2, principal.tenantId().toString());
                    ps.setString(3, principal.highestRole());
                    ps.execute();
                }
            });

            registerClearOnCompletion(entityManager);
        });
    }

    private boolean requireTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            return true;
        }
        log.warn("Tenant identity requested outside an active transaction — skipping; a "
                + "transaction-local setting would not survive to the query. Ensure the caller "
                + "is @Transactional.");
        return false;
    }

    /**
     * Belt-and-braces clear. PostgreSQL already drops a transaction-local setting at commit or
     * rollback, so unlike the Oracle original this is not the load-bearing cleanup — it just keeps
     * the identity from lingering if the connection is reused within the same transaction scope.
     */
    private void registerClearOnCompletion(EntityManager entityManager) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCompletion() {
                try {
                    entityManager.unwrap(Session.class).doWork(connection -> {
                        try (PreparedStatement ps = connection.prepareStatement(CLEAR_IDENTITY_SQL)) {
                            ps.execute();
                        }
                    });
                } catch (Exception e) {
                    log.warn("Tenant identity clear failed: {}", e.getMessage());
                }
            }
        });
    }
}
