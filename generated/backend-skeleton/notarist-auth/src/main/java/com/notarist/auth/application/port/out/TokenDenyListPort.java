package com.notarist.auth.application.port.out;

import java.time.Duration;

/**
 * Port for JWT access token deny-listing on logout.
 * Backed by Redis in production; in-memory for local/dev.
 * Entry TTL = remaining access token validity.
 */
public interface TokenDenyListPort {
    void addToDenyList(String jti, Duration ttl);
    boolean isDenied(String jti);
}
