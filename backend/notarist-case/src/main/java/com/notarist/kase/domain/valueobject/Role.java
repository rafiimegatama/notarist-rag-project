package com.notarist.kase.domain.valueobject;

/**
 * The roles the Case context cares about, mirroring the EXISTING {@code notarist-auth} Role enum
 * (STAFF, NOTARIS, PPAT_OFFICER, PIMPINAN, ADMIN).
 *
 * <p>Duplicated here deliberately rather than depended upon: the Case domain must not depend on the
 * auth module (that would drag security infrastructure into the domain layer). SYSTEM is added for
 * transitions performed by a background worker, which has no human actor.
 *
 * <p>The values are kept identical to the auth enum so the application layer maps them 1:1 from the
 * JWT claims that already exist. No new claim, and no change to authentication, is required.
 */
public enum Role {
    STAFF,
    NOTARIS,
    PPAT_OFFICER,
    PIMPINAN,
    ADMIN,
    SYSTEM
}
