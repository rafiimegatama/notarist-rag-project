package com.notarist.auth.infrastructure.security;

import com.notarist.core.security.VpdContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Applies Oracle VPD context for the current user principal before each Oracle query.
 * Uses Hibernate's Session.doWork to execute DBMS_SESSION.SET_CONTEXT over the raw JDBC connection.
 */
@Component("authVpdContextApplier")
public class VpdContextApplier {

    private static final String SET_CTX_SQL =
            "{call DBMS_SESSION.SET_CONTEXT(?, ?, ?)}";
    private static final String VPD_NAMESPACE = "NOTARIST_CTX";

    public void applyIfPresent(EntityManager entityManager) {
        VpdContextHolder.get().ifPresent(principal -> {
            Session hibernateSession = entityManager.unwrap(Session.class);
            hibernateSession.doWork(connection -> {
                try (var cs = connection.prepareCall(SET_CTX_SQL)) {
                    cs.setString(1, VPD_NAMESPACE);
                    cs.setString(2, "USER_ID");
                    cs.setString(3, principal.userId().toString());
                    cs.execute();

                    cs.setString(2, "TENANT_ID");
                    cs.setString(3, principal.tenantId().toString());
                    cs.execute();

                    cs.setString(2, "USER_ROLE");
                    cs.setString(3, principal.highestRole());
                    cs.execute();
                }
            });
        });
    }
}
