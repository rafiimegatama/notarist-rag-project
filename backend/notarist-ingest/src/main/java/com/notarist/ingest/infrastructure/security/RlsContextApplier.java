package com.notarist.ingest.infrastructure.security;

import com.notarist.core.security.VpdContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;

/**
 * Ingest module tenant-identity applier — local copy to enforce module isolation (no auth/document
 * import).
 *
 * <p>Backed by PostgreSQL RLS (Flyway V9), which replaced Oracle VPD. The identity lives in
 * transaction-local session settings written by {@code notarist_set_identity()}; the policy on
 * {@code ingestion_job} reads them back through {@code current_setting()}. Callers must be
 * @Transactional so the setting survives to the query.

 * <p><b>There is deliberately no clear-on-completion hook.</b> One used to be registered on
 * {@code beforeCompletion()}. Spring commits as triggerBeforeCommit -> triggerBeforeCompletion ->
 * doCommit, and Hibernate flushes INSERT/UPDATE in doCommit, so the clear blanked
 * {@code notarist.tenant_id} BEFORE the write was flushed and the policy's WITH CHECK rejected it
 * ("new row violates row-level security policy"). It broke writes only — reads flush inside the
 * method — and only once RLS was genuinely enforced, since a BYPASSRLS role skips policies entirely
 * and hid it. It was never needed: {@code notarist_set_identity()} uses {@code set_config(..., TRUE)},
 * so PostgreSQL drops the setting at commit or rollback by itself.
 */
@Component("ingestRlsContextApplier")
public class RlsContextApplier {

    private static final Logger log = LoggerFactory.getLogger(RlsContextApplier.class);

    private static final String SET_IDENTITY_SQL = "SELECT notarist_set_identity(?, ?, ?)";
    private static final String SET_SYSTEM_SQL     = "SELECT notarist_set_system_identity()";

    /**
     * Applies the caller's identity, falling back to a trusted system session when there is no
     * principal.
     *
     * <p>The ingest repositories are reached from two directions: an HTTP upload (authenticated —
     * the principal's tenant applies) and the background pipeline workers driven by
     * {@code IngestionQueueScheduler} (no principal, and they legitimately process jobs across
     * every tenant). The tenant policy is fail-closed (Flyway V9), so without this fallback the
     * workers would silently see zero rows and the whole pipeline would stall.
     *
     * <p>This exemption is confined to the ingest job/queue tables. It is NOT available on
     * dokumen_legal — the document repository has no system path, so the legal content stays
     * strictly tenant-filtered by the database.
     */
    public void applyPrincipalOrSystem(EntityManager entityManager) {
        if (VpdContextHolder.get().isPresent()) {
            applyIfPresent(entityManager);
            return;
        }

        if (!requireTransaction()) {
            return;
        }

        try {
            entityManager.unwrap(Session.class).doWork(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SET_SYSTEM_SQL)) {
                    ps.execute();
                }
            });
        } catch (Exception e) {
            log.warn("System identity apply failed: {}", e.getMessage());
            return;
        }

    }

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            if (!requireTransaction()) {
                return;
            }

            try {
                entityManager.unwrap(Session.class).doWork(connection -> {
                    try (PreparedStatement ps = connection.prepareStatement(SET_IDENTITY_SQL)) {
                        ps.setString(1, principal.userId().toString());
                        ps.setString(2, principal.tenantId().toString());
                        ps.setString(3, principal.highestRole());
                        ps.execute();
                    }
                });
            } catch (Exception e) {
                // Fail-closed: with no identity established the RLS policy shows the caller
                // nothing, so a failed apply degrades to "no rows", never to "every tenant's rows".
                log.warn("Tenant identity apply failed — queries will return no rows: {}", e.getMessage());
                return;
            }

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

}
