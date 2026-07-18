package com.notarist.auth.infrastructure.bootstrap;

import com.notarist.auth.application.service.PasswordVerifier;
import com.notarist.auth.infrastructure.persistence.postgres.UserJpaEntity;
import com.notarist.auth.infrastructure.persistence.postgres.UserJpaRepository;
import com.notarist.auth.infrastructure.persistence.postgres.UserRoleJpaEntity;
import com.notarist.auth.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Development-only login bootstrap: a fresh clone must reach a working login without manual SQL.
 *
 * <p>Runs only under the {@code local} / {@code dev} profiles — never in production, where account
 * creation stays an explicit administrative act. Idempotent: if the username already exists (in ANY
 * tenant — the lookup runs as a system session, same exemption as the login lookup), nothing is
 * written and the existing account is left untouched, including its password.
 *
 * <p>The user id is derived deterministically from the username so repeated boots against the same
 * database converge instead of accumulating rows. The password is BCrypt-hashed through the same
 * {@link PasswordVerifier} the login flow verifies against — no second hashing policy to drift.
 */
@Component
@Profile({"local", "dev"})
public class DevUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserBootstrap.class);

    private final UserJpaRepository users;
    private final PasswordVerifier passwordVerifier;
    private final RlsContextApplier rlsContextApplier;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private final String username;
    private final String password;
    private final String tenantId;
    private final String fullName;
    private final String roleCode;

    public DevUserBootstrap(
            UserJpaRepository users,
            PasswordVerifier passwordVerifier,
            RlsContextApplier rlsContextApplier,
            PlatformTransactionManager transactionManager,
            @Value("${DEV_SEED_USERNAME:demo.notaris}") String username,
            @Value("${DEV_SEED_PASSWORD:NotaristDemo123!}") String password,
            @Value("${DEV_SEED_TENANT_ID:a0000000-0000-4000-8000-000000000001}") String tenantId,
            @Value("${DEV_SEED_FULL_NAME:Demo Notaris}") String fullName,
            @Value("${DEV_SEED_ROLE:NOTARIS}") String roleCode) {
        this.users = users;
        this.passwordVerifier = passwordVerifier;
        this.rlsContextApplier = rlsContextApplier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.username = username;
        this.password = password;
        this.tenantId = tenantId;
        this.fullName = fullName;
        this.roleCode = roleCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(status -> {
            rlsContextApplier.applySystemIdentity(entityManager);
            if (users.findByUsername(username).isPresent()) {
                log.info("Dev bootstrap: user '{}' already exists — leaving it untouched", username);
                return;
            }
            String userId = UUID.nameUUIDFromBytes(
                    ("notarist-dev-seed:" + username).getBytes(StandardCharsets.UTF_8)).toString();
            UserJpaEntity user = new UserJpaEntity(
                    userId, tenantId, username,
                    passwordVerifier.encode(password),
                    fullName, true, null, Instant.now());
            user.getRoles().add(new UserRoleJpaEntity(userId, roleCode));
            users.save(user);
            log.info("Dev bootstrap: created user '{}' (role {}, tenant {}) — password from "
                    + "DEV_SEED_PASSWORD", username, roleCode, tenantId);
        });
    }
}
