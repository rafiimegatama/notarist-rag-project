package com.notarist.verification.infrastructure.security;

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
 * Establishes the row-level-security identity for the current principal before a verification query
 * runs.
 *
 * <p>A copy of the other modules' applier rather than a shared one, on purpose: module isolation keeps
 * notarist-verification from importing notarist-auth. Backed by the same PostgreSQL RLS machinery
 * (Flyway V9's {@code notarist_set_identity()}), which the verification policy in V13 reads back
 * through {@code current_setting()}. Fail-closed: a session with no identity sees no verifications.
 * Callers must be {@code @Transactional}.
 */
@Component("verificationRlsContextApplier")
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
