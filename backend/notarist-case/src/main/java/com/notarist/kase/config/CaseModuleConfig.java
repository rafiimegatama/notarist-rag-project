package com.notarist.kase.config;

import com.notarist.kase.application.listener.IngestionOutcomeHandler;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.infrastructure.observability.CaseMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Case module Spring configuration.
 *
 * <p>Most beans are auto-discovered via {@code @Service} / {@code @Repository} / {@code @Component}
 * and scanned by the root context; JPA entities under infrastructure.persistence.postgres are picked
 * up by the root EntityManagerFactory's {@code @EntityScan("com.notarist")}.
 *
 * <p>The one explicit bean is {@link IngestionOutcomeHandler}: it is a framework-free plain class (it
 * takes only ports), so it is wired here rather than annotated. It is driven by
 * {@code DocumentIngestionCompletedListener}, which resolves the owning case from the ingested
 * document and hands it a {@code DocumentIngestionOutcome}.
 */
@Configuration
public class CaseModuleConfig {

    @Bean
    public IngestionOutcomeHandler ingestionOutcomeHandler(
            CaseRepository caseRepository, TimelineRepository timelineRepository,
            DomainEventPublisher eventPublisher, CaseMetrics metrics) {
        return new IngestionOutcomeHandler(caseRepository, timelineRepository, eventPublisher, metrics);
    }
}
