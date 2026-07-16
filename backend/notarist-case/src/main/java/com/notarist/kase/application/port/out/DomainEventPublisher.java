package com.notarist.kase.application.port.out;

import com.notarist.core.domain.event.DomainEvent;

import java.util.List;

/**
 * Publishes the events an aggregate raised, after it has been saved.
 *
 * <p>Declared as a port rather than depending on Spring's publisher directly, so the domain and
 * application layers stay free of framework types and the events can later move to a durable broker
 * without touching a single aggregate.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
