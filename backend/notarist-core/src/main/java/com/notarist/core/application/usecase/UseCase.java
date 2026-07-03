package com.notarist.core.application.usecase;

/**
 * Generic use-case interface for command/query operations that return a result.
 * @param <C> Command or Query input type
 * @param <R> Result type
 */
@FunctionalInterface
public interface UseCase<C, R> {
    R execute(C command);
}
