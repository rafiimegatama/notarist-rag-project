package com.notarist.review.infrastructure.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.review.application.port.out.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adapts the {@link DomainEventPublisher} port onto Spring's {@link ApplicationEventPublisher}. The
 * domain and application layers depend only on the port, so they never see a Spring type; when the
 * events later need a durable broker, this adapter is the only thing that changes.
 *
 * <p>Explicit bean name: other bounded contexts scan a class of the same simple name, so the default
 * (class-derived) name would collide under the root {@code @ComponentScan("com.notarist")}.
 */
@Component("reviewSpringDomainEventPublisher")
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
