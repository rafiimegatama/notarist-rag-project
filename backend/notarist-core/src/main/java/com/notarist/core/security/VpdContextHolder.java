package com.notarist.core.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local holder for the caller's tenant-isolation context.
 * Set by JwtAuthenticationFilter; read by the repository implementations, which push it into
 * PostgreSQL as transaction-local settings that the row-level-security policies read back
 * (see RlsContextApplier and Flyway V9). The "Vpd" name is a holdover from the Oracle VPD
 * implementation this replaced; the contract is unchanged.
 * Must be cleared in filter finally-block to prevent leaking across requests.
 */
public final class VpdContextHolder {

    private static final ThreadLocal<VpdPrincipal> CONTEXT = new ThreadLocal<>();

    private VpdContextHolder() {}

    public static void set(VpdPrincipal principal) {
        CONTEXT.set(principal);
    }

    public static Optional<VpdPrincipal> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public record VpdPrincipal(
            UUID userId,
            UUID tenantId,
            String highestRole
    ) {
        public VpdPrincipal {
            if (userId == null) throw new IllegalArgumentException("userId required");
            if (tenantId == null) throw new IllegalArgumentException("tenantId required");
            if (highestRole == null || highestRole.isBlank()) throw new IllegalArgumentException("highestRole required");
        }
    }
}
