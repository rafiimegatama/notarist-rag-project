package com.notarist.kase.infrastructure;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.application.query.CaseFilter;
import com.notarist.kase.domain.factory.CaseFactory;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseNumber;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.domain.valueobject.Role;
import com.notarist.kase.infrastructure.persistence.postgres.CaseJpaEntity;
import com.notarist.kase.infrastructure.persistence.postgres.CaseJpaRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository integration test against a REAL PostgreSQL (Testcontainers), running the production
 * Flyway chain (V1..V10, pulled in via the notarist-infra test dependency) and connecting as a
 * dedicated non-superuser role so row-level security is actually enforced.
 *
 * <p>What it proves for the vertical slice:
 * <ul>
 *   <li>a Case and its Timeline round-trip through the mapper/entities intact;</li>
 *   <li>the filter/pagination query returns the right rows in the right order;</li>
 *   <li>tenant isolation holds at the database — a case saved under tenant A is invisible to B;</li>
 *   <li>the {@code @Version} column enforces optimistic locking on concurrent edits.</li>
 * </ul>
 */
@SpringBootTest
@EnabledIf("dockerAvailable")
class CaseRepositoryIntegrationTest {

    // Manual lifecycle (no testcontainers-junit-jupiter dependency needed): a static container,
    // started lazily in the property source. Ryuk reaps it when the test JVM exits. The whole class
    // is skipped (not failed) where Docker is unavailable — the constructor below touches no daemon.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notarist")
            .withInitScript("case-it-bootstrap-role.sql");

    /** Class-level gate: evaluated before the Spring context loads, so a Docker-less box skips cleanly. */
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
        // Connect and migrate as the non-superuser application role created by the init script, so
        // FORCE ROW LEVEL SECURITY takes effect (a superuser would bypass every policy).
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
    @EntityScan("com.notarist.kase.infrastructure.persistence.postgres")
    @EnableJpaRepositories("com.notarist.kase.infrastructure.persistence.postgres")
    @ComponentScan(basePackages = {
            "com.notarist.kase.infrastructure.persistence",
            "com.notarist.kase.infrastructure.security"
    })
    static class ItConfig {
    }

    @Autowired CaseRepository caseRepository;
    @Autowired TimelineRepository timelineRepository;
    @Autowired CaseJpaRepository caseJpaRepository;
    @Autowired com.notarist.kase.application.port.out.CaseAnalyticsRepository analytics;
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

    private CaseFactory.NewCase newCase(String number, CaseType type, UUID tenantId, UUID staff) {
        return CaseFactory.create(CaseNumber.of(number), type, tenantId,
                Actor.of(staff, Role.STAFF), null, CorrelationId.generate(), null);
    }

    @Test
    @DisplayName("a case and its timeline round-trip through PostgreSQL intact")
    void roundTrip() {
        asTenant(tenantA, staffA, Role.STAFF);
        CaseFactory.NewCase created = newCase("1/V/2026", CaseType.FIDUSIA, tenantA, staffA);
        caseRepository.save(created.aCase());
        timelineRepository.save(created.timeline());

        Case loaded = caseRepository.findById(created.aCase().caseId()).orElseThrow();
        assertThat(loaded.caseNumber().value()).isEqualTo("1/V/2026");
        assertThat(loaded.caseType()).isEqualTo(CaseType.FIDUSIA);
        assertThat(loaded.state()).isEqualTo(CaseState.CASE_CREATED);
        assertThat(loaded.tenantId()).isEqualTo(tenantA);

        Timeline timeline = timelineRepository.findByCase(created.aCase().caseId()).orElseThrow();
        assertThat(timeline.entryCount()).isEqualTo(1);
        assertThat(timeline.entries().get(0).type().name()).isEqualTo("CASE_OPENED");
    }

    @Test
    @DisplayName("a status change persists and is reloaded, with the new timeline entry")
    void statusChangePersists() {
        asTenant(tenantA, staffA, Role.STAFF);
        CaseFactory.NewCase created = newCase("2/V/2026", CaseType.ROYA, tenantA, staffA);
        caseRepository.save(created.aCase());
        timelineRepository.save(created.timeline());

        Case aCase = caseRepository.findById(created.aCase().caseId()).orElseThrow();
        aCase.transition(CaseState.UPLOADING, Actor.of(staffA, Role.STAFF));
        caseRepository.save(aCase);

        Timeline timeline = timelineRepository.findByCase(aCase.caseId()).orElseThrow();
        timeline.append(com.notarist.kase.domain.model.TimelineEntryType.STATE_CHANGED,
                "Status CASE_CREATED → UPLOADING", Actor.of(staffA, Role.STAFF), null, null);
        timelineRepository.save(timeline);

        assertThat(caseRepository.findById(aCase.caseId()).orElseThrow().state())
                .isEqualTo(CaseState.UPLOADING);
        assertThat(timelineRepository.findByCase(aCase.caseId()).orElseThrow().entryCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("filter + pagination returns the matching rows, newest first")
    void filterAndPaginate() {
        asTenant(tenantA, staffA, Role.STAFF);
        // Three FIDUSIA + one ROYA, spaced in time so ordering is deterministic.
        for (int i = 1; i <= 3; i++) {
            CaseFactory.NewCase c = newCase(i + "/VI/2026", CaseType.FIDUSIA, tenantA, staffA);
            caseRepository.save(c.aCase());
        }
        caseRepository.save(newCase("9/VI/2026", CaseType.ROYA, tenantA, staffA).aCase());

        CaseFilter fidusia = new CaseFilter(null, CaseType.FIDUSIA, null, null, null, null);
        assertThat(caseRepository.count(tenantA, fidusia)).isEqualTo(3);

        List<Case> firstPage = caseRepository.search(tenantA, fidusia, 0, 2);
        assertThat(firstPage).hasSize(2);
        List<Case> secondPage = caseRepository.search(tenantA, fidusia, 1, 2);
        assertThat(secondPage).hasSize(1);

        CaseFilter byCreatedBy = new CaseFilter(null, null, null, staffA, null, null);
        assertThat(caseRepository.count(tenantA, byCreatedBy)).isEqualTo(4);

        CaseFilter future = new CaseFilter(null, null, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null);
        assertThat(caseRepository.count(tenantA, future)).isZero();
    }

    @Test
    @DisplayName("row-level security hides a case from another tenant")
    void tenantIsolation() {
        asTenant(tenantA, staffA, Role.STAFF);
        CaseFactory.NewCase created = newCase("7/V/2026", CaseType.AJB, tenantA, staffA);
        caseRepository.save(created.aCase());
        CaseId caseId = created.aCase().caseId();

        // Same id, different tenant identity: the policy makes it invisible.
        asTenant(tenantB, UUID.randomUUID(), Role.STAFF);
        assertThat(caseRepository.findById(caseId)).isEmpty();
        assertThat(caseRepository.count(tenantB, CaseFilter.empty())).isZero();

        // Back to the owning tenant: visible again.
        asTenant(tenantA, staffA, Role.STAFF);
        assertThat(caseRepository.findById(caseId)).isPresent();
    }

    @Test
    @DisplayName("a fail-closed policy shows nothing when no tenant identity is established")
    void failClosedWithoutIdentity() {
        asTenant(tenantA, staffA, Role.STAFF);
        caseRepository.save(newCase("8/V/2026", CaseType.KUASA, tenantA, staffA).aCase());

        VpdContextHolder.clear();   // no identity at all
        assertThat(caseRepository.count(tenantA, CaseFilter.empty())).isZero();
    }

    @Test
    @DisplayName("analytics aggregates are computed in SQL, tenant-scoped, in single queries")
    void analyticsAggregates() {
        // A fresh tenant so counts are isolated from the other tests' data.
        UUID tenantC = UUID.randomUUID();
        UUID staffC = UUID.randomUUID();
        asTenant(tenantC, staffC, Role.STAFF);

        caseRepository.save(newCase("1/VII/2026", CaseType.FIDUSIA, tenantC, staffC).aCase());
        caseRepository.save(newCase("2/VII/2026", CaseType.FIDUSIA, tenantC, staffC).aCase());
        caseRepository.save(newCase("3/VII/2026", CaseType.SKMHT, tenantC, staffC).aCase());

        // countByType — one GROUP BY query.
        Map<CaseType, Long> byType = analytics.countByType(tenantC);
        assertThat(byType).containsEntry(CaseType.FIDUSIA, 2L).containsEntry(CaseType.SKMHT, 1L);

        // countByState — all freshly opened, so CASE_CREATED = 3.
        assertThat(analytics.countByState(tenantC)).containsEntry(CaseState.CASE_CREATED, 3L);

        // windowCounts — all three created "now", nothing closed.
        Instant dayStart = Instant.now().minus(1, ChronoUnit.HOURS);
        var w = analytics.windowCounts(tenantC, dayStart, dayStart, dayStart);
        assertThat(w.total()).isEqualTo(3);
        assertThat(w.today()).isEqualTo(3);
        assertThat(w.averageProcessingSeconds()).isNull();

        // monthlyTrend — a single bucket this month.
        Instant since = Instant.now().minus(365, ChronoUnit.DAYS);
        assertThat(analytics.monthlyTrend(tenantC, since))
                .anySatisfy(m -> assertThat(m.count()).isGreaterThanOrEqualTo(3));

        // reminderCandidates — only the SKMHT case qualifies (non-terminal, deadline-bearing).
        assertThat(analytics.reminderCandidates(tenantC))
                .extracting(r -> r.caseType())
                .containsExactly(CaseType.SKMHT);
    }

    @Test
    @DisplayName("the @Version column enforces optimistic locking on a concurrent edit")
    void optimisticLocking() {
        CaseFactory.NewCase created = newCase("5/V/2026", CaseType.SKMHT, tenantA, staffA);
        String id = created.aCase().caseId().value().toString();

        // Insert through the RLS-aware repository so the row passes WITH CHECK.
        asTenant(tenantA, staffA, Role.STAFF);
        caseRepository.save(created.aCase());
        VpdContextHolder.clear();

        // Two independent, detached copies loaded before either write — both at version 0.
        CaseJpaEntity copyOne = inTenant(() -> caseJpaRepository.findById(id).orElseThrow());
        CaseJpaEntity copyTwo = inTenant(() -> caseJpaRepository.findById(id).orElseThrow());
        assertThat(copyOne.getVersion()).isEqualTo(copyTwo.getVersion());

        // First writer wins, bumping the version.
        inTenant(() -> {
            copyOne.setState(CaseState.UPLOADING.name());
            return caseJpaRepository.saveAndFlush(copyOne);
        });

        // Second writer, still on the stale version, is rejected.
        assertThatThrownBy(() -> inTenant(() -> {
            copyTwo.setState(CaseState.CANCELLED.name());
            return caseJpaRepository.saveAndFlush(copyTwo);
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
