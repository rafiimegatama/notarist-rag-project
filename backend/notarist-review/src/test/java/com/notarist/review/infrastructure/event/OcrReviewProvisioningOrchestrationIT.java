package com.notarist.review.infrastructure.event;

import com.notarist.core.api.event.OcrReviewProvisioningRequested;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.review.application.port.out.OcrReviewRepository;
import com.notarist.review.domain.valueobject.DocumentId;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the OCR→Review provisioning orchestration end to end inside a real Spring Boot context
 * against a real PostgreSQL (embedded server binary, no Docker), running the production Flyway chain
 * and connecting as a non-superuser, NOBYPASSRLS role that OWNS the tables — so RLS is enforced.
 *
 * <p>This is a runtime proof, not an inspection. It drives the exact production path:
 * publish {@link OcrReviewProvisioningRequested} inside a committed transaction, and assert that the
 * {@code @TransactionalEventListener(AFTER_COMMIT)} {@link OcrReviewProvisioningListener} fired, opened
 * its own transaction, installed the event's tenant identity, and wrote a real {@code ocr_review} row.
 * Bean graph, event dispatch, transaction boundary and RLS write are all exercised together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OcrReviewProvisioningOrchestrationIT {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws Exception {
        pg = EmbeddedPostgres.start();
        // A dedicated app role, as production requires: not a superuser, no BYPASSRLS. Flyway runs as
        // it, so it owns the tables and FORCE ROW LEVEL SECURITY (V14) actually constrains it.
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE ROLE notarist_app LOGIN PASSWORD 'notarist_app' "
                    + "NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE");
            st.execute("GRANT ALL ON SCHEMA public TO notarist_app");
        }
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("notarist_app", "postgres"));
        registry.add("spring.datasource.username", () -> "notarist_app");
        registry.add("spring.datasource.password", () -> "notarist_app");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/postgres/flyway");
        registry.add("spring.flyway.user", () -> "notarist_app");
        registry.add("spring.flyway.password", () -> "notarist_app");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (pg != null) pg.close();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.notarist.review.infrastructure.persistence.postgres")
    @EnableJpaRepositories("com.notarist.review.infrastructure.persistence.postgres")
    @ComponentScan(basePackages = {
            "com.notarist.review.application.service",
            "com.notarist.review.infrastructure.persistence",
            "com.notarist.review.infrastructure.security",
            "com.notarist.review.infrastructure.event"
    })
    static class ItConfig {
    }

    @Autowired ApplicationEventPublisher publisher;
    @Autowired OcrReviewRepository repository;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void clearContext() {
        VpdContextHolder.clear();
    }

    @Test
    @DisplayName("publishing the OCR-completion event provisions a real ocr_review row via the listener")
    void ocrCompletionEventProvisionsReviewRow() {
        UUID documentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID uploadedBy = UUID.randomUUID();

        // Publish inside a committed transaction so the AFTER_COMMIT listener is dispatched. The
        // listener installs the event's tenant identity itself and writes the review in its own tx.
        tx.executeWithoutResult(status ->
                publisher.publishEvent(new OcrReviewProvisioningRequested(
                        documentId, tenantId, uploadedBy, "KTP.pdf", 2, 0.88)));

        // Prove the row is really in the database — visible under the owning tenant's RLS identity.
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(uploadedBy, tenantId, "STAFF"));
        Boolean exists = tx.execute(s -> repository.existsByDocumentId(DocumentId.of(documentId)));
        assertThat(exists).isTrue();

        // And it must be tenant-scoped: another tenant cannot see it.
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "STAFF"));
        Boolean visibleToOther = tx.execute(s -> repository.existsByDocumentId(DocumentId.of(documentId)));
        assertThat(visibleToOther).isFalse();
    }

    @Test
    @DisplayName("duplicate delivery is idempotent — the second event does not throw back to the publisher")
    void duplicateDeliveryIsIdempotent() {
        UUID documentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID uploadedBy = UUID.randomUUID();

        OcrReviewProvisioningRequested event = new OcrReviewProvisioningRequested(
                documentId, tenantId, uploadedBy, "KTP.pdf", 2, 0.88);

        // First delivery provisions the review; the second must be a clean no-op. The risk under test:
        // the listener's REQUIRES_NEW transaction, combined with the "already exists" invariant thrown
        // by initializeReview, marking the transaction rollback-only and surfacing an
        // UnexpectedRollbackException back through the committing publisher transaction. If that happens,
        // the second publish below throws and this test fails.
        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        tx.executeWithoutResult(status -> publisher.publishEvent(event));

        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(uploadedBy, tenantId, "STAFF"));
        Boolean exists = tx.execute(s -> repository.existsByDocumentId(DocumentId.of(documentId)));
        assertThat(exists).isTrue();
    }
}
