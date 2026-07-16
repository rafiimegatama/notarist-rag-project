package com.notarist.review.domain.valueobject;

/**
 * The roles the Review context cares about, mirroring the EXISTING {@code notarist-auth} Role enum
 * (STAFF, NOTARIS, PPAT_OFFICER, PIMPINAN, ADMIN). SYSTEM is added for completeness but a review
 * decision is always a human act — the aggregate refuses a SYSTEM reviewer.
 *
 * <p>Duplicated here deliberately rather than depended upon: the Review domain must not depend on the
 * auth module. Values are kept identical to the auth enum so the application layer maps them 1:1 from
 * the JWT claims that already exist — no new claim, no change to authentication.
 */
public enum Role {
    STAFF,
    NOTARIS,
    PPAT_OFFICER,
    PIMPINAN,
    ADMIN,
    SYSTEM
}
