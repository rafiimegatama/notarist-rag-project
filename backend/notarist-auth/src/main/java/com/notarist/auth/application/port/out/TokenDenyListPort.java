package com.notarist.auth.application.port.out;

import java.time.Duration;

/**
 * Port for JWT access token deny-listing on logout.
 * Backed by the shared PostgreSQL token_deny_list table (durable and cluster-wide) —
 * see TokenDenyListRepositoryImpl for why PostgreSQL rather than Redis.
 * Entry TTL = remaining access token validity.
 */
public interface TokenDenyListPort {
    void addToDenyList(String jti, Duration ttl);
    boolean isDenied(String jti);
}
