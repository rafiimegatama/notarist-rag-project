package com.notarist.kase.config;

import com.notarist.kase.application.listener.IngestionOutcomeHandler;
import com.notarist.kase.application.port.in.HandleIngestionOutcomeUseCase;
import com.notarist.kase.application.port.out.BundleRepository;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.infrastructure.event.DocumentIngestionCompletedListener;
import com.notarist.kase.infrastructure.observability.CaseMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Boots the ingestion→case orchestration slice as a real Spring context — external ports mocked, so no
 * database or Docker is required — to prove the bean graph loads and is self-consistent. The unit tests
 * exercise each bean's logic in isolation; this is the one place the wiring itself is executed.
 *
 * <p>What it asserts: the context refreshes, {@link IngestionOutcomeHandler} resolves to exactly one
 * {@link HandleIngestionOutcomeUseCase} bean, and {@link DocumentIngestionCompletedListener} is
 * constructed with its dependencies satisfied. It component-scans the (single-class)
 * {@code application.listener} package, so if a <em>second, differently-named</em> handler bean were
 * ever introduced there, the {@code hasSize(1)} assertion — and, in the real app, the listener's own
 * injection point — would fail with {@code NoUniqueBeanDefinitionException} at build time rather than
 * at deploy. (A same-named {@code @Service} would instead be silently overridden by the {@code @Bean},
 * so that is a code-smell for review, not a crash this test can force.)
 */
class IngestionOrchestrationWiringTest {

    @Test
    @DisplayName("the ingestion→case orchestration wires with exactly one outcome-handler bean")
    void orchestrationWiresWithSingleUseCaseBean() {
        assertThatCode(() -> {
            try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
                // Match Spring Boot's default: a second definition of the same bean is a startup error,
                // not a silent override. Without this, a stray @Service on IngestionOutcomeHandler
                // (same bean name as the @Bean) would be quietly collapsed instead of caught.
                ctx.setAllowBeanDefinitionOverriding(false);
                ctx.register(MockPorts.class, CaseModuleConfig.class, DocumentIngestionCompletedListener.class);
                // Surfaces a stray @Service/@Component on IngestionOutcomeHandler as a duplicate definition.
                ctx.scan("com.notarist.kase.application.listener");
                ctx.refresh();

                assertThat(ctx.getBeanNamesForType(HandleIngestionOutcomeUseCase.class)).hasSize(1);
                assertThat(ctx.getBean(DocumentIngestionCompletedListener.class)).isNotNull();
                assertThat(ctx.getBean(IngestionOutcomeHandler.class)).isNotNull();
            }
        }).doesNotThrowAnyException();
    }

    /** The infrastructure ports the orchestration beans depend on, all mocked — no DB, no Docker. */
    @Configuration
    static class MockPorts {
        @Bean CaseRepository caseRepository() { return mock(CaseRepository.class); }
        @Bean TimelineRepository timelineRepository() { return mock(TimelineRepository.class); }
        @Bean DomainEventPublisher domainEventPublisher() { return mock(DomainEventPublisher.class); }
        @Bean CaseMetrics caseMetrics() { return mock(CaseMetrics.class); }
        @Bean BundleRepository bundleRepository() { return mock(BundleRepository.class); }
    }
}
