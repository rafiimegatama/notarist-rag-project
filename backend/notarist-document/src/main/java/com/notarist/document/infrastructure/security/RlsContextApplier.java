package com.notarist.document.infrastructure.security;

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
 * Document module's own instance — module isolation prevents importing from notarist-auth.
 *
 * <p>Backed by PostgreSQL RLS (Flyway V9), which replaced Oracle VPD. The identity lives in
 * transaction-local session settings written by {@code notarist_set_identity()}; the policy on
 * {@code dokumen_legal} reads them back through {@code current_setting()}.
 *
 * <p>Note what this class does NOT have: a system-identity escape. {@code dokumen_legal} holds the
 * sensitive legal content and every path to it carries a real principal, so it stays strictly
 * tenant-filtered by the database. Do not add one.
 *
 * <p>Callers must be @Transactional: the settings are transaction-local, so outside a transaction
 * the write would not survive to the query, and the fail-closed policy would return zero rows.
 */
@Component("documentRlsContextApplier")
public class RlsContextApplier {

    private static final Logger log = LoggerFactory.getLogger(RlsContextApplier.class);

    private static final String SET_IDENTITY_SQL   = "SELECT notarist_set_identity(?, ?, ?)";
    private static final String CLEAR_IDENTITY_SQL = "SELECT notarist_clear_identity()";

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                log.warn("Tenant identity requested outside an active transaction — skipping; a "
                        + "transaction-local setting would not survive to the query. Ensure the "
                        + "caller is @Transactional.");
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
        });
    }
}
