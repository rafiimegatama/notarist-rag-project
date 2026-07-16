package com.notarist.review.infrastructure;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.review.application.port.out.OcrReviewRepository;
import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.Reviewer;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.domain.valueobject.Role;
import com.notarist.review.infrastructure.persistence.postgres.OcrReviewFieldAuditJpaRepository;
import com.notarist.review.infrastructure.persistence.postgres.OcrReviewJpaEntity;
import com.notarist.review.infrastructure.persistence.postgres.OcrReviewJpaRepository;
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
 * Flyway chain (V1..V12, pulled in via the notarist-infra test dependency) and connecting as a
 * dedicated non-superuser role so row-level security is actually enforced.
 *
 * <p>What it proves:
 * <ul>
 *   <li>a review, its fields, its authority items and its append-only audit round-trip intact;</li>
 *   <li>tenant isolation holds at the database — a review saved under tenant A is invisible to B;</li>
 *   <li>a fail-closed policy shows nothing when no tenant identity is established;</li>
 *   <li>the {@code @Version} column enforces optimistic locking on concurrent reviewers.</li>
 * </ul>
 */
@SpringBootTest
@EnabledIf("dockerAvailable")
class OcrReviewRepositoryIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notarist")
            .withInitScript("review-it-bootstrap-role.sql");

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
    @EntityScan("com.notarist.review.infrastructure.persistence.postgres")
    @EnableJpaRepositories("com.notarist.review.infrastructure.persistence.postgres")
    @ComponentScan(basePackages = {
            "com.notarist.review.infrastructure.persistence",
            "com.notarist.review.infrastructure.security"
    })
    static class ItConfig {
    }

    @Autowired OcrReviewRepository repository;
    @Autowired OcrReviewJpaRepository jpaRepository;
    @Autowired OcrReviewFieldAuditJpaRepository auditRepository;
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

    private OcrReview newReview(UUID documentId, UUID tenantId) {
        FieldReview f1 = FieldReview.extracted(FieldId.generate(), "NIK", "NIK", "3174091205880003",
                0.98, BoundingBox.of(1, 0.32, 0.18, 0.4, 0.05), 0);
        FieldReview f2 = FieldReview.extracted(FieldId.generate(), "Alamat", "Alamat", "JL MELATI 12",
                0.72, BoundingBox.of(1, 0.32, 0.42, 0.55, 0.08), 1);
        return OcrReview.start(ReviewId.generate(), documentId, tenantId, "KTP.pdf", 1,
                false, true, 0.85, List.of(f1, f2), List.of());
    }

    @Test
    @DisplayName("a review, its fields and its audit trail round-trip through PostgreSQL")
    void roundTrip() {
        UUID documentId = UUID.randomUUID();
        asTenant(tenantA, staffA, Role.STAFF);
        OcrReview review = newReview(documentId, tenantA);
        FieldId firstField = review.fields().get(0).fieldId();
        repository.save(review);

        // Apply a decision through the aggregate + repository, then reload and assert it persisted.
        OcrReview loaded = repository.findByDocumentId(DocumentId.of(documentId)).orElseThrow();
        loaded.reviewField(firstField, FieldDecision.CORRECTED, "3174091205880009", null,
                Reviewer.of(staffA, Role.STAFF), null, null);
        repository.save(loaded);

        OcrReview reloaded = repository.findByDocumentId(DocumentId.of(documentId)).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        FieldReview corrected = reloaded.fields().stream()
                .filter(f -> f.fieldId().equals(firstField)).findFirst().orElseThrow();
        assertThat(corrected.decision()).isEqualTo(FieldDecision.CORRECTED);
        assertThat(corrected.extractedValue()).isEqualTo("3174091205880003");   // original preserved
        assertThat(corrected.correctedValue()).isEqualTo("3174091205880009");

        // The append-only audit history has exactly the one decision.
        assertThat(auditRepository.findByReviewIdOrderBySequenceAsc(reloaded.reviewId().value().toString()))
                .hasSize(1)
                .allSatisfy(a -> assertThat(a.getDecision()).isEqualTo("CORRECTED"));
    }

    @Test
    @DisplayName("row-level security hides a review from another tenant, and fails closed with no identity")
    void tenantIsolationAndFailClosed() {
        UUID documentId = UUID.randomUUID();
        asTenant(tenantA, staffA, Role.STAFF);
        repository.save(newReview(documentId, tenantA));

        asTenant(tenantB, UUID.randomUUID(), Role.STAFF);
        assertThat(repository.findByDocumentId(DocumentId.of(documentId))).isEmpty();

        VpdContextHolder.clear();   // no identity at all → fail closed
        assertThat(repository.findByDocumentId(DocumentId.of(documentId))).isEmpty();

        asTenant(tenantA, staffA, Role.STAFF);
        assertThat(repository.findByDocumentId(DocumentId.of(documentId))).isPresent();
    }

    @Test
    @DisplayName("the @Version column rejects a concurrent reviewer editing the same document")
    void optimisticLockingOnConcurrentReview() {
        UUID documentId = UUID.randomUUID();
        asTenant(tenantA, staffA, Role.STAFF);
        OcrReview review = newReview(documentId, tenantA);
        String id = review.reviewId().value().toString();
        repository.save(review);
        VpdContextHolder.clear();

        // Two independent, detached copies loaded before either write — both at the same version.
        OcrReviewJpaEntity copyOne = inTenant(() -> jpaRepository.findById(id).orElseThrow());
        OcrReviewJpaEntity copyTwo = inTenant(() -> jpaRepository.findById(id).orElseThrow());
        assertThat(copyOne.getVersion()).isEqualTo(copyTwo.getVersion());

        // First reviewer wins, stamping the root and bumping the version.
        inTenant(() -> {
            copyOne.setReviewStatus(ReviewStatus.IN_PROGRESS.name());
            copyOne.setReviewedAt(Instant.now());
            return jpaRepository.saveAndFlush(copyOne);
        });

        // Second reviewer, still on the stale version, is rejected.
        assertThatThrownBy(() -> inTenant(() -> {
            copyTwo.setReviewStatus(ReviewStatus.IN_PROGRESS.name());
            copyTwo.setReviewedAt(Instant.now());
            return jpaRepository.saveAndFlush(copyTwo);
        })).isInstanceOf(OptimisticLockingFailureException.class);
    }

    /** Runs a unit of work in one transaction with tenant A's RLS identity established first. */
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
