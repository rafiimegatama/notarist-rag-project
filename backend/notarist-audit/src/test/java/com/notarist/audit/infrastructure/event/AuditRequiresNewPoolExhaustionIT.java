package com.notarist.audit.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression guard for the dedicated-audit-datasource architecture (Option A).
 *
 * <p>Before: the audit write used the shared business pool via {@code REQUIRES_NEW}, so an audited
 * operation inside a transaction needed a SECOND business connection while it still held the first —
 * under concurrency the pool starved and audited operations failed. This test USED to prove that
 * failure; it now proves the fix.
 *
 * <p>After: {@code AuditTrailRepositoryImpl} writes on a dedicated, autocommit audit pool
 * ({@code auditJdbcTemplate}) separate from the business pool. Two invariants must hold:
 * <ol>
 *   <li><b>Business pool exhausted → audit still succeeds.</b> The whole business pool is held; a
 *       fail-closed {@code SECURITY_ACCESS_DENIED} audit is published and still commits, because it
 *       draws from the independent audit pool. (If someone reverted the repository to the business
 *       {@code postgresJdbcTemplate}, this write would starve and the test would fail — the guard.)</li>
 *   <li><b>Fail-closed semantics unchanged.</b> When the audit pool itself cannot supply a
 *       connection, a fail-closed event still propagates the failure out to the caller, so the
 *       business operation still fails closed.</li>
 * </ol>
 *
 * <p>The two datasources point at the same embedded PostgreSQL. The business pool is {@code @Primary}
 * (Flyway and JPA boot on it) and sized 2 so startup succeeds; the audit pool mirrors
 * {@code AuditConnectionConfig} (minimumIdle 0, autocommit) with a short acquisition timeout so the
 * fail-closed path is observable, not a multi-second hang.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditRequiresNewPoolExhaustionIT {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws Exception {
        pg = EmbeddedPostgres.start();
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/postgres/flyway");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) pg.close();
    }

    private static HikariDataSource pool(String name, int maxPoolSize, int minimumIdle) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(pg.getJdbcUrl("postgres", "postgres"));
        config.setUsername("postgres");
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(2000);
        config.setAutoCommit(true);
        config.setPoolName(name);
        return new HikariDataSource(config);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {
            "com.notarist.audit.application.handler",
            "com.notarist.audit.infrastructure.persistence"
    })
    @Import(AuditEventListener.class)
    static class ItConfig {
        // Business pool: @Primary so Flyway/JPA boot on it; sized 2 so startup succeeds and the test
        // can exhaust it by holding both connections.
        @Bean("postgresDataSource")
        @Primary
        DataSource postgresDataSource() {
            return pool("BusinessPool-Test", 2, 2);
        }

        @Bean("postgresJdbcTemplate")
        JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource ds) {
            return new JdbcTemplate(ds);
        }

        // Audit pool: mirrors AuditConnectionConfig (minimumIdle 0, autocommit), separate from business.
        @Bean("auditDataSource")
        DataSource auditDataSource() {
            return pool("AuditPool-Test", 2, 0);
        }

        @Bean("auditJdbcTemplate")
        JdbcTemplate auditJdbcTemplate(@Qualifier("auditDataSource") DataSource ds) {
            return new JdbcTemplate(ds);
        }
    }

    @Autowired ApplicationEventPublisher publisher;
    @Autowired @Qualifier("postgresDataSource") DataSource businessDataSource;
    @Autowired @Qualifier("auditDataSource") DataSource auditDataSource;
    @Autowired @Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbcTemplate;

    /** Publish a fail-closed SECURITY_ACCESS_DENIED event synchronously through the listener. */
    private void publishSecurityDenied(String reason) {
        publisher.publishEvent(new AuditEventPayload(
                "SECURITY_ACCESS_DENIED", "BUNDLE", UUID.randomUUID().toString(),
                UUID.randomUUID(), "STAFF", UUID.randomUUID(),
                "READ", "FAILURE", "10.0.0.1", UUID.randomUUID().toString(),
                Map.of("reason", reason)));
    }

    @Test
    @DisplayName("business pool exhausted → the fail-closed audit still commits on the dedicated audit pool")
    void auditSucceedsWhileBusinessPoolExhausted() throws Exception {
        // Hold BOTH business connections for the duration of the audit — the business pool is fully
        // exhausted. With the shared-pool design this starved the audit; with the dedicated audit pool
        // it must not.
        try (Connection b1 = businessDataSource.getConnection();
             Connection b2 = businessDataSource.getConnection()) {
            assertThatCode(() -> publishSecurityDenied("business-exhausted"))
                    .doesNotThrowAnyException();
        }

        Long count = auditJdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_trail WHERE event_type = ?", Long.class, "SECURITY_ACCESS_DENIED");
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("audit pool exhausted → a fail-closed event still propagates the failure (fail-closed preserved)")
    void failClosedStillPropagatesWhenAuditPoolExhausted() throws Exception {
        // Exhaust the audit pool itself (size 2). The fail-closed SECURITY event's audit write can then
        // acquire no connection and must surface the failure to the caller — the business operation
        // stays fail-closed.
        try (Connection a1 = auditDataSource.getConnection();
             Connection a2 = auditDataSource.getConnection()) {
            assertThatThrownBy(() -> publishSecurityDenied("audit-exhausted"))
                    .hasStackTraceContaining("Connection is not available");
        }
    }
}
