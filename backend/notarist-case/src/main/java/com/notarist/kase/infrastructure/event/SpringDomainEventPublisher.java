package com.notarist.kase.infrastructure.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adapts the {@link DomainEventPublisher} port onto Spring's {@link ApplicationEventPublisher}.
 *
 * <p>The domain and application layers depend only on the port, so they never see a Spring type. When
 * the events later need a durable broker, this adapter is the only thing that changes — no aggregate
 * and no handler is touched.
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
