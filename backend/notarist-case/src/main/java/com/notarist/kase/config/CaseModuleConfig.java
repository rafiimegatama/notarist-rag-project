package com.notarist.kase.config;

import com.notarist.kase.application.listener.IngestionOutcomeHandler;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
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
 * takes only ports), so it is wired here rather than annotated. It stays inert until the composition
 * root translates an ingest completion into a {@code DocumentIngestionOutcome} — a change to
 * notarist-ingest that is out of scope for this sprint — but the boundary bean exists now so nothing
 * is tempted to reach into a Case aggregate directly.
 */
@Configuration
public class CaseModuleConfig {

    @Bean
    public IngestionOutcomeHandler ingestionOutcomeHandler(
            CaseRepository caseRepository, DomainEventPublisher eventPublisher) {
        return new IngestionOutcomeHandler(caseRepository, eventPublisher);
    }
}
