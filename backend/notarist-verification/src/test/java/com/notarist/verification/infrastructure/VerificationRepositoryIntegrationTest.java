package com.notarist.verification.infrastructure;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.verification.application.port.out.VerificationRepository;
import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.Reviewer;
import com.notarist.verification.domain.valueobject.Role;
import com.notarist.verification.domain.valueobject.VerificationId;
import com.notarist.verification.infrastructure.persistence.postgres.VerificationItemAuditJpaRepository;
import com.notarist.verification.infrastructure.persistence.postgres.VerificationJpaEntity;
import com.notarist.verification.infrastructure.persistence.postgres.VerificationJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository integration test against a REAL PostgreSQL (Testcontainers), running the production
 * Flyway chain (V1..V13, pulled in via the notarist-infra test dependency) and connecting as a
 * dedicated non-superuser role so row-level security is actually enforced.
 *
 * <p>What it proves: round-trip of a verification + its checklist + append-only audit; tenant
 * isolation and fail-closed under RLS; and the {@code @Version} column rejecting a concurrent verifier.
 */
@SpringBootTest
@EnabledIf("dockerAvailable")
class VerificationRepositoryIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notarist")
            .withInitScript("verification-it-bootstrap-role.sql");

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notarist_app");
        registry.add("spring.datasource.password", () -> "notarist_app");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/postgres/flyway");
        registry.add("spring.flyway.user", () -> "notarist_app");
        registry.add("spring.flyway.password", () -> "notarist_app");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.notarist.verification.infrastructure.persistence.postgres")
    @EnableJpaRepositories("com.notarist.verification.infrastructure.persistence.postgres")
    @ComponentScan(basePackages = {
            "com.notarist.verification.infrastructure.persistence",
            "com.notarist.verification.infrastructure.security"
    })
    static class ItConfig {
    }

    @Autowired VerificationRepository repository;
    @Autowired VerificationJpaRepository jpaRepository;
    @Autowired VerificationItemAuditJpaRepository auditRepository;
    @Autowired TransactionTemplate txTemplate;

    @PersistenceContext EntityManager entityManager;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID staffA = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        VpdContextHolder.clear();
    }

    private void asTenant(UUID tenantId, UUID userId, Role role) {
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(userId, tenantId, role.name()));
    }

    private Verification newVerification(UUID bundleId, UUID tenantId) {
        ChecklistItem a = ChecklistItem.create(ItemId.generate(), ChecklistCategory.AUTHORITY,
                "Authority clause", true, CheckType.AUTOMATIC, 0);
        ChecklistItem b = ChecklistItem.create(ItemId.generate(), ChecklistCategory.IDENTITY,
                "Identity match", true, CheckType.MANUAL, 1);
        return Verification.start(VerificationId.generate(), bundleId, tenantId, List.of(a, b));
    }

    @Test
    @DisplayName("a verification, its checklist and its audit trail round-trip through PostgreSQL")
    void roundTrip() {
        UUID bundleId = UUID.randomUUID();
        asTenant(tenantA, staffA, Role.STAFF);
        Verification verification = newVerification(bundleId, tenantA);
        ItemId firstItem = verification.items().get(0).itemId();
        repository.save(verification);

        Verification loaded = repository.findByBundleId(BundleId.of(bundleId)).orElseThrow();
        loaded.decideItem(firstItem, Decision.FAIL, "authority mismatch",
                Reviewer.of(staffA, Role.STAFF), null, null);
        repository.save(loaded);

        Verification reloaded = repository.findByBundleId(BundleId.of(bundleId)).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(VerificationStatus.UNDER_VERIFICATION);
        assertThat(reloaded.failedCount()).isEqualTo(1);
        assertThat(reloaded.items().stream().filter(i -> i.itemId().equals(firstItem)).findFirst().orElseThrow()
                .comment()).isEqualTo("authority mismatch");

        assertThat(auditRepository.findByVerificationIdOrderBySequenceAsc(
                reloaded.verificationId().value().toString()))
                .hasSize(1)
                .allSatisfy(a -> assertThat(a.getDecision()).isEqualTo("FAIL"));
    }

    @Test
    @DisplayName("row-level security hides a verification from another tenant, and fails closed with no identity")
    void tenantIsolationAndFailClosed() {
        UUID bundleId = UUID.randomUUID();
        asTenant(tenantA, staffA, Role.STAFF);
        repository.save(newVerification(bundleId, tenantA));

        asTenant(tenantB, UUID.randomUUID(), Role.STAFF);
        assertThat(repository.findByBundleId(BundleId.of(bundleId))).isEmpty();

        VpdContextHolder.clear();
        assertThat(repository.findByBundleId(BundleId.of(bundleId))).isEmpty();

        asTenant(tenantA, staffA, Role.STAFF);
        assertThat(repository.findByBundleId(BundleId.of(bundleId))).isPresent();
    }

    @Test
    @DisplayName("the @Version column rejects a concurrent verifier editing the same bundle")
    void optimisticLockingOnConcurrentVerification() {
        UUID bundleId = UUID.randomUUID();
        asTenant(tenantA, staffA, Role.STAFF);
        Verification verification = newVerification(bundleId, tenantA);
        String id = verification.verificationId().value().toString();
        repository.save(verification);
        VpdContextHolder.clear();

        VerificationJpaEntity copyOne = inTenant(() -> jpaRepository.findById(id).orElseThrow());
        VerificationJpaEntity copyTwo = inTenant(() -> jpaRepository.findById(id).orElseThrow());
        assertThat(copyOne.getVersion()).isEqualTo(copyTwo.getVersion());

        inTenant(() -> {
            copyOne.setStatus(VerificationStatus.UNDER_VERIFICATION.name());
            copyOne.setReviewedAt(Instant.now());
            return jpaRepository.saveAndFlush(copyOne);
        });

        assertThatThrownBy(() -> inTenant(() -> {
            copyTwo.setStatus(VerificationStatus.UNDER_VERIFICATION.name());
            copyTwo.setReviewedAt(Instant.now());
            return jpaRepository.saveAndFlush(copyTwo);
        })).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private <T> T inTenant(Supplier<T> body) {
        return txTemplate.execute(status -> {
            entityManager.createNativeQuery("SELECT notarist_set_identity(:u, :t, :r)")
                    .setParameter("u", staffA.toString())
                    .setParameter("t", tenantA.toString())
                    .setParameter("r", Role.STAFF.name())
                    .getSingleResult();
            return body.get();
        });
    }
}
