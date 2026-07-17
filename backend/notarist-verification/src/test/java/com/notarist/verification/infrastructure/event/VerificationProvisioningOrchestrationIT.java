package com.notarist.verification.infrastructure.event;

import com.notarist.core.api.event.VerificationProvisioningRequested;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.verification.application.port.out.VerificationRepository;
import com.notarist.verification.domain.valueobject.BundleId;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import com.notarist.verification.application.port.out.VerificationFactsPort;
import com.notarist.verification.infrastructure.VerificationFactsPortDefaultAdapter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
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
 * Executes the Bundle→Verification provisioning orchestration end to end in a real Spring Boot context
 * against a real PostgreSQL (embedded server binary, no Docker), with RLS enforced by a non-superuser
 * owning role. Proves that publishing {@link VerificationProvisioningRequested} inside a committed
 * transaction causes the {@code AFTER_COMMIT} {@link VerificationProvisioningListener} to open its own
 * {@code REQUIRES_NEW} transaction and write a real {@code verification} row — the same
 * transaction-boundary fix the review path needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VerificationProvisioningOrchestrationIT {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws Exception {
        pg = EmbeddedPostgres.start();
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
    @EntityScan("com.notarist.verification.infrastructure.persistence.postgres")
    @EnableJpaRepositories("com.notarist.verification.infrastructure.persistence.postgres")
    @ComponentScan(basePackages = {
            "com.notarist.verification.application.service",
            "com.notarist.verification.infrastructure.persistence",
            "com.notarist.verification.infrastructure.security",
            "com.notarist.verification.infrastructure.event"
    })
    static class ItConfig {
        // The default facts port lives in the root infrastructure package (empty facts → checklist is
        // the manual items only); provide it explicitly rather than widen the scan into the JPA repos.
        @Bean
        VerificationFactsPort verificationFactsPort() {
            return new VerificationFactsPortDefaultAdapter();
        }
    }

    @Autowired ApplicationEventPublisher publisher;
    @Autowired VerificationRepository repository;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void clearContext() {
        VpdContextHolder.clear();
    }

    @Test
    @DisplayName("publishing the bundle-ready event provisions a real verification row via the listener")
    void bundleReadyEventProvisionsVerificationRow() {
        UUID bundleId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        tx.executeWithoutResult(status ->
                publisher.publishEvent(new VerificationProvisioningRequested(bundleId, tenantId, actor)));

        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(actor, tenantId, "NOTARIS"));
        Boolean exists = tx.execute(s -> repository.existsByBundleId(BundleId.of(bundleId)));
        assertThat(exists).isTrue();

        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "NOTARIS"));
        Boolean visibleToOther = tx.execute(s -> repository.existsByBundleId(BundleId.of(bundleId)));
        assertThat(visibleToOther).isFalse();
    }
}
