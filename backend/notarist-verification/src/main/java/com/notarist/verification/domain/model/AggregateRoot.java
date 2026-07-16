package com.notarist.verification.domain.model;

import com.notarist.core.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every aggregate root in the Verification bounded context.
 *
 * <p>It makes two things true without each aggregate having to remember them:
 * <ol>
 *   <li><b>{@code validate()}</b> — the aggregate can always be asked whether it is internally
 *       consistent; called after every mutation, so it can never be left in a forbidden state.</li>
 *   <li><b>{@code domainEvents()}</b> — every state change records what happened; events accumulate
 *       here and are drained exactly once by the application layer.</li>
 * </ol>
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /** @throws com.notarist.verification.domain.exception.VerificationInvariantViolationException when violated */
    public abstract void validate();

    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }

    public boolean hasUncommittedEvents() {
        return !domainEvents.isEmpty();
    }

    protected void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    protected void enforceInvariants() {
        validate();
    }
}
