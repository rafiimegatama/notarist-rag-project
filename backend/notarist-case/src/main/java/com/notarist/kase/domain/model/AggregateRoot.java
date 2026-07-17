package com.notarist.kase.domain.model;

import com.notarist.core.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every aggregate root in the Case bounded context.
 *
 * <p>It exists to make three things true for all of them, rather than leaving each aggregate to
 * remember:
 *
 * <ol>
 *   <li><b>{@code validate()}</b> — the aggregate can always be asked whether it is internally
 *       consistent. Called after every mutation, so an aggregate can never be left in a state its own
 *       invariants forbid.</li>
 *   <li><b>{@code transition(...)}</b> — the ONLY way state changes. There are no public setters
 *       anywhere in this package; illegal transitions are rejected by the aggregate itself, not by a
 *       service that a future caller could bypass.</li>
 *   <li><b>{@code domainEvents()}</b> — every state change records what happened. Events are
 *       accumulated here and drained exactly once by the application layer.</li>
 * </ol>
 *
 * <p>This deliberately does not repeat the pattern used by the existing {@code DocumentLegal}
 * aggregate, where {@code transitionStatus()} is an unguarded field assignment carrying a
 * {@code // TODO: enforce state machine transitions} and the rules live in a static helper that any
 * caller can simply not call. Rules that live beside an aggregate are advice. Rules that live inside
 * it are invariants.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Asserts every invariant of this aggregate.
     *
     * @throws com.notarist.kase.domain.exception.InvariantViolationException when violated
     */
    public abstract void validate();

    /** Events raised since this aggregate was loaded. Read-only. */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Drains the recorded events. Called once by the application layer after a successful save, so an
     * event cannot be published twice for one state change.
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }

    public boolean hasUncommittedEvents() {
        return !domainEvents.isEmpty();
    }

    /**
     * Records an event. Protected: only the aggregate itself may raise its own events — an event is a
     * statement about something the aggregate did, so nobody else is in a position to make it.
     */
    protected void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Runs {@link #validate()} after a mutation. Every transition ends with this. */
    protected void enforceInvariants() {
        validate();
    }
}
