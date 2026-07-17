package com.notarist.kase.infrastructure.security;

import com.notarist.core.security.VpdContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;

/**
 * Establishes the row-level-security identity for the current principal before a Case query runs.
 *
 * <p>A copy of the Document module's applier rather than a shared one, on purpose: module isolation
 * keeps notarist-case from importing notarist-auth or notarist-document. Backed by the same
 * PostgreSQL RLS machinery (Flyway V9's {@code notarist_set_identity()}), which the case policies in
 * V10 read back through {@code current_setting()}.
 *
 * <p>No system-identity escape. Cases are always reached with a real authenticated principal — the
 * ingestion handler that advances a case on SYSTEM's behalf still runs inside a request-derived
 * identity when wired, and there is no legitimate cross-tenant path to a case. Fail-closed: a session
 * with no identity sees no cases.
 *
 * <p>Callers must be {@code @Transactional}; the settings are transaction-local, so outside a
 * transaction the write would not survive to the query and the policy would return zero rows.
 */
@Component("caseRlsContextApplier")
public class RlsContextApplier {

    private static final Logger log = LoggerFactory.getLogger(RlsContextApplier.class);

    private static final String SET_IDENTITY_SQL = "SELECT notarist_set_identity(?, ?, ?)";

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

            // No clear hook here, deliberately. There used to be one, registered on
            // beforeCompletion(), and it broke every write this module makes.
            //
            // Spring commits in the order: triggerBeforeCommit -> triggerBeforeCompletion ->
            // doCommit. Hibernate does not flush an INSERT when save() is called; it flushes in
            // doCommit. So beforeCompletion() ran notarist_clear_identity() FIRST, blanking
            // notarist.tenant_id, and the INSERT then arrived with no identity and was rejected by
            // the policy's WITH CHECK:
            //
            //   ERROR: new row violates row-level security policy for table "notarial_case"
            //
            // Reads were unaffected — they flush and execute inside the method — which is why this
            // only surfaces on writes, and only once RLS is genuinely enforced (a BYPASSRLS role
            // skips the policy and hides the bug entirely).
            //
            // Clearing is unnecessary regardless: notarist_set_identity() uses set_config(..., TRUE),
            // so the setting is transaction-local and PostgreSQL discards it at commit or rollback.
            // V9 makes exactly this point — the leak "is closed by the database, not by remembering
            // to call a cleanup hook". The hook was an Oracle-era leftover that had no upside and a
            // large downside.
        });
    }
}
