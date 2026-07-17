package com.notarist.auth.infrastructure.persistence.postgres;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Durable, cluster-wide JWT deny-list backed by PostgreSQL {@code token_deny_list} (Flyway V7).
 *
 * Replaces the previous ConcurrentHashMap implementation (F14), under which a logout was honoured
 * only by the instance that served it and was lost on restart — behind a load balancer a revoked
 * access token stayed valid on every peer until natural expiry.
 *
 * <h3>Why PostgreSQL and not Redis</h3>
 * The port javadoc suggested Redis, but there is no Redis service in infra/docker/docker-compose.yml
 * (the stack is MinIO + PostgreSQL + Qdrant + Ollama). Introducing Redis would add a new piece of
 * production infrastructure — a new failure domain, new ops burden — to solve a problem PostgreSQL,
 * already present and already holding session_token, solves adequately. The deny-list is small
 * (one row per logout, for at most the access-token TTL), and the lookup is a single primary-key
 * hit. Redis remains the right answer only if per-request lookup latency is later shown to matter;
 * the port abstraction makes that swap a one-class change.
 *
 * <h3>Consistency</h3>
 * Deliberately no local cache. A negative (not-denied) cache would recreate the very staleness
 * F14 is about: a peer's revocation would not be observed until the cache expired. Every
 * {@code isDenied} check therefore reads the shared table — an indexed PK lookup on the shared
 * HikariCP pool.
 *
 * Uses the shared "postgresJdbcTemplate" bean from notarist-infra PostgresConnectionConfig, the
 * same convention as SessionTokenRepositoryImpl.
 */
@Repository
public class TokenDenyListRepositoryImpl implements TokenDenyListPort {

    private static final Logger log = LoggerFactory.getLogger(TokenDenyListRepositoryImpl.class);

    // Re-revoking the same jti (double logout, or a retry) must be idempotent, and must never
    // shorten an existing entry — GREATEST keeps the longest-lived expiry.
    private static final String SQL_DENY = """
            INSERT INTO token_deny_list (jti, expires_at, revoked_at)
            VALUES (?, ?, ?)
            ON CONFLICT (jti) DO UPDATE
                SET expires_at = GREATEST(token_deny_list.expires_at, EXCLUDED.expires_at)
            """;

    // Expiry is enforced in the predicate, not only by the purge job: a row that the purge has
    // not reached yet must already read as not-denied once its TTL has passed.
    private static final String SQL_IS_DENIED = """
            SELECT COUNT(*) FROM token_deny_list
            WHERE jti = ? AND expires_at > NOW()
            """;

    private static final String SQL_PURGE_EXPIRED = """
            DELETE FROM token_deny_list WHERE expires_at <= NOW()
            """;

    private final JdbcTemplate postgresJdbcTemplate;

    public TokenDenyListRepositoryImpl(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    @Override
    public void addToDenyList(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;  // nothing to revoke, or the token has already expired on its own
        }
        Instant now = Instant.now();
        postgresJdbcTemplate.update(
                SQL_DENY,
                jti,
                Timestamp.from(now.plus(ttl)),
                Timestamp.from(now)
        );
    }

    @Override
    public boolean isDenied(String jti) {
        if (jti == null || jti.isBlank()) return false;
        Long count = postgresJdbcTemplate.queryForObject(SQL_IS_DENIED, Long.class, jti);
        return count != null && count > 0;
    }

    /**
     * Purges rows whose token has expired naturally — the deny-list entry is then redundant,
     * because JWT signature/exp validation rejects the token on its own.
     * Repurposes the eviction schedule of the old in-memory implementation (every 5 minutes).
     * Running on every instance is harmless: DELETE of already-deleted rows is a no-op.
     */
    @Scheduled(fixedDelay = 300_000)
    public void evictExpiredEntries() {
        try {
            int purged = postgresJdbcTemplate.update(SQL_PURGE_EXPIRED);
            if (purged > 0) {
                log.debug("Purged {} expired token deny-list entries", purged);
            }
        } catch (RuntimeException e) {
            // A failed purge is a housekeeping problem, never a security one — stale rows only
            // ever deny a token that is already invalid. Do not let it kill the scheduler thread.
            log.warn("Token deny-list purge failed: {}", e.getMessage());
        }
    }
}
