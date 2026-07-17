package com.notarist.audit.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
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
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SRE reproduction: the audit {@code REQUIRES_NEW} write needs a SECOND pooled connection while the
 * caller's transaction still holds the first. When the pool cannot supply it, an audited operation
 * inside a transaction stalls until the connection-acquisition timeout and then fails.
 *
 * <p>Deterministic single-thread proof. The pool must be big enough to <em>boot</em> — a size-1 pool
 * starves Flyway during startup and the context never loads, which proves nothing about the runtime
 * path. So the pool is 2 (enough for sequential Flyway-then-app startup) and the test recreates the
 * "no free connection for the audit" state at runtime: it holds one connection itself, leaving exactly
 * one free. The business transaction then consumes that last one on its {@code SELECT 1}, so the audit's
 * REQUIRES_NEW transaction can never acquire a second and fails after the acquisition timeout. In
 * production this is the N-concurrent-audited-operations deadlock (each holds one connection and waits
 * for another) — the capacity cost the REQUIRES_NEW audit fix introduced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditRequiresNewPoolExhaustionIT {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws Exception {
        pg = EmbeddedPostgres.start();
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/postgres/flyway");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // Two connections: enough for startup (Flyway, then the app, acquire sequentially), but small
        // enough that the test can starve the pool by holding one while the business tx takes the other.
        // Short acquisition timeout so the audit's failure is observable, not a multi-second hang.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) pg.close();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {
            "com.notarist.audit.application.handler",
            "com.notarist.audit.infrastructure.persistence"
    })
    @Import(AuditEventListener.class)
    static class ItConfig {
        @Bean("postgresJdbcTemplate")
        @Primary
        JdbcTemplate postgresJdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        PoolProbe poolProbe(ApplicationEventPublisher publisher,
                            @Qualifier("postgresJdbcTemplate") JdbcTemplate jdbc) {
            return new PoolProbe(publisher, jdbc);
        }
    }

    static class PoolProbe {
        private final ApplicationEventPublisher publisher;
        private final JdbcTemplate jdbc;

        PoolProbe(ApplicationEventPublisher publisher, JdbcTemplate jdbc) {
            this.publisher = publisher;
            this.jdbc = jdbc;
        }

        @Transactional
        void workThenAudit() {
            jdbc.queryForObject("SELECT 1", Integer.class);   // binds the single connection to this tx
            // SECURITY is fail-closed: an audit that cannot be written fails the business operation.
            publisher.publishEvent(new AuditEventPayload(
                    "SECURITY_ACCESS_DENIED", "BUNDLE", UUID.randomUUID().toString(),
                    UUID.randomUUID(), "STAFF", UUID.randomUUID(),
                    "READ", "FAILURE", "10.0.0.1", UUID.randomUUID().toString(),
                    Map.of("reason", "pool-exhaustion-probe")));
        }
    }

    @Autowired PoolProbe probe;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("an audited operation cannot complete when the pool cannot supply the audit's 2nd connection")
    void requiresNewAuditStarvesForAConnection() throws Exception {
        // Occupy one of the two pooled connections for the duration of the call. The business tx then
        // takes the last free one, so the audit's REQUIRES_NEW tx finds none — the production deadlock,
        // reproduced single-threaded.
        try (Connection held = dataSource.getConnection()) {
            assertThatThrownBy(() -> probe.workThenAudit())
                    .hasStackTraceContaining("Connection is not available");
        }
    }
}
