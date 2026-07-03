package com.notarist.ingest.infrastructure.security;

import com.notarist.core.security.VpdContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;

/** Ingest module VPD applier — local copy to enforce module isolation (no auth/document import). */
@Component
public class VpdContextApplier {

    private static final Logger log = LoggerFactory.getLogger(VpdContextApplier.class);

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            entityManager.unwrap(Session.class).doWork(conn -> {
                try (CallableStatement cs = conn.prepareCall(
                        "{ call DBMS_SESSION.SET_CONTEXT('NOTARIST_CTX', ?, ?) }")) {
                    cs.setString(1, "USER_ID");
                    cs.setString(2, principal.userId().toString());
                    cs.execute();

                    cs.setString(1, "TENANT_ID");
                    cs.setString(2, principal.tenantId().toString());
                    cs.execute();

                    cs.setString(1, "USER_ROLE");
                    cs.setString(2, principal.highestRole());
                    cs.execute();
                } catch (Exception e) {
                    log.warn("VPD context apply failed — continuing without tenant isolation: {}", e.getMessage());
                }
            });
        });
    }
}
