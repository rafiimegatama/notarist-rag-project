package com.notarist.verification.domain.valueobject;

/**
 * The roles the Verification context cares about, mirroring the EXISTING {@code notarist-auth} Role
 * enum (STAFF, NOTARIS, PPAT_OFFICER, PIMPINAN, ADMIN). SYSTEM is added for completeness but a
 * verification decision is always a human act.
 *
 * <p>Duplicated here deliberately rather than depended upon: the Verification domain must not depend
 * on the auth module. Values are kept identical to the auth enum so the application layer maps them
 * 1:1 from the JWT claims that already exist — no new claim, no change to authentication.
 */
public enum Role {
    STAFF,
    NOTARIS,
    PPAT_OFFICER,
    PIMPINAN,
    ADMIN,
    SYSTEM
}
