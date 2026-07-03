package com.notarist.auth.infrastructure.cache;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token deny list. Acceptable for single-instance deployments;
 * replace with Redis-backed implementation for multi-node.
 * Expired entries are evicted every 5 minutes via scheduled task.
 */
@Component
public class TokenDenyListImpl implements TokenDenyListPort {

    private final ConcurrentHashMap<String, Instant> deniedTokens = new ConcurrentHashMap<>();

    @Override
    public void addToDenyList(String jti, Duration ttl) {
        deniedTokens.put(jti, Instant.now().plus(ttl));
    }

    @Override
    public boolean isDenied(String jti) {
        Instant expiry = deniedTokens.get(jti);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            deniedTokens.remove(jti);
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpiredEntries() {
        Instant now = Instant.now();
        deniedTokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}
