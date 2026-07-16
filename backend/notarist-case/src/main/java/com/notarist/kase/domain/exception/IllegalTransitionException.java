package com.notarist.kase.domain.exception;

/**
 * A transition that the state machine does not allow was attempted.
 *
 * <p>This is thrown by the aggregate, not by a service. That is the whole point: an illegal
 * transition is not "rejected by validation", it is <em>impossible</em> — there is no code path that
 * mutates the state field without going through the machine that raises this.
 */
public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(String message) {
        super(message);
    }

    public static IllegalTransitionException of(Object aggregate, Object from, Object to) {
        return new IllegalTransitionException(
                aggregate.getClass().getSimpleName() + ": illegal transition " + from + " → " + to);
    }
}
