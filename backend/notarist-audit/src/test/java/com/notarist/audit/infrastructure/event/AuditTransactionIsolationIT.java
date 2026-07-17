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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves, by execution against a real PostgreSQL, that a SECURITY audit record survives the rollback
 * of the business transaction that produced it.
 *
 * <p>The regulated access trail is written by a plain {@code @EventListener} on the caller's thread.
 * Independence from the caller's transaction now comes from the dedicated, autocommit audit datasource
 * ({@code AuditConnectionConfig} / {@code auditJdbcTemplate}) — a separate pool, enlisted in no caller
 * transaction, so the INSERT commits immediately. The production path {@code loadForCaller(...)}
 * publishes {@code SECURITY_ACCESS_DENIED} and then throws a not-found exception, rolling that
 * transaction back; the audit row has already committed on the audit connection and stays on disk.
 *
 * <p>This models exactly that path: audit-then-throw inside {@code @Transactional} (bound to the
 * business pool), then assert the row is on disk. It would fail (count 0) if the audit write shared the
 * caller's transaction, and passes (count 1) because the audit datasource is independent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditTransactionIsolationIT {

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

    private static HikariDataSource pool(String name, boolean autoCommit) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(pg.getJdbcUrl("postgres", "postgres"));
        config.setUsername("postgres");
        config.setMaximumPoolSize(2);
        config.setAutoCommit(autoCommit);
        config.setPoolName(name);
        return new HikariDataSource(config);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    // Scan only handler + persistence; import the real listener explicitly so we do NOT sweep in the
    // sibling ITs' nested @Configuration classes that share this package.
    @ComponentScan(basePackages = {
            "com.notarist.audit.application.handler",
            "com.notarist.audit.infrastructure.persistence"
    })
    @Import(AuditEventListener.class)
    static class ItConfig {
        // Business pool: @Primary, backs the caller's @Transactional and the verification query.
        @Bean("postgresDataSource")
        @Primary
        DataSource postgresDataSource() {
            return pool("BusinessPool-IsolationIT", true);
        }

        @Bean("postgresJdbcTemplate")
        JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource ds) {
            return new JdbcTemplate(ds);
        }

        // Audit pool: the separate autocommit datasource the audit repository writes on — enlisted in
        // no caller transaction, so its INSERT commits independently of the business rollback.
        @Bean("auditDataSource")
        DataSource auditDataSource() {
            return pool("AuditPool-IsolationIT", true);
        }

        @Bean("auditJdbcTemplate")
        JdbcTemplate auditJdbcTemplate(@Qualifier("auditDataSource") DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        TxProbe txProbe(ApplicationEventPublisher publisher) {
            return new TxProbe(publisher);
        }
    }

    /** Stands in for a service like BundleTransitionService.loadForCaller: audit a denial, then throw. */
    static class TxProbe {
        private final ApplicationEventPublisher publisher;

        TxProbe(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        void denyAndRollback(String subjectId) {
            publisher.publishEvent(new AuditEventPayload(
                    "SECURITY_ACCESS_DENIED", "BUNDLE", subjectId,
                    UUID.randomUUID(), "STAFF", UUID.randomUUID(),
                    "READ", "FAILURE", "10.0.0.1", UUID.randomUUID().toString(),
                    Map.of("reason", "CROSS_TENANT_ACCESS")));
            throw new IllegalStateException("business operation rejected after the denial was audited");
        }
    }

    @Autowired TxProbe probe;
    @Autowired @Qualifier("postgresJdbcTemplate") JdbcTemplate jdbc;

    @Test
    @DisplayName("a SECURITY_ACCESS_DENIED audit survives the rollback of the operation that raised it")
    void securityAuditSurvivesCallerRollback() {
        String subjectId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> probe.denyAndRollback(subjectId))
                .isInstanceOf(IllegalStateException.class);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM audit_trail WHERE subject_id = ?", Integer.class, subjectId);
        assertThat(count).isEqualTo(1);
    }
}
