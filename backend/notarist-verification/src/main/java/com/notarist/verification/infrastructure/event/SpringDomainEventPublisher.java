package com.notarist.verification.infrastructure.event;

import com.notarist.core.domain.event.DomainEvent;
import com.notarist.verification.application.port.out.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adapts the {@link DomainEventPublisher} port onto Spring's {@link ApplicationEventPublisher}.
 *
 * <p>Explicit bean name: other bounded contexts scan a class of the same simple name, so the default
 * (class-derived) name would collide under the root {@code @ComponentScan("com.notarist")}.
 */
@Component("verificationSpringDomainEventPublisher")
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
