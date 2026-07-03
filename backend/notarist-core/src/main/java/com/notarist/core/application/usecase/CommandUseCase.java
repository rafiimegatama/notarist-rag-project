package com.notarist.core.application.usecase;

/**
 * Use-case interface for commands that produce no meaningful return value.
 * @param <C> Command input type
 */
@FunctionalInterface
public interface CommandUseCase<C> {
    void execute(C command);
}
