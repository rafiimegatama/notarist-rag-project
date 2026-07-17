package com.notarist.core.api.event;

import java.util.UUID;

/**
 * Integration event: a bundle was sealed (locked), so its verification checklist should be
 * provisioned. Published by notarist-case when a {@code BundleLocked} domain event commits; consumed
 * by notarist-verification to build the automatic + manual checklist for the bundle.
 *
 * <p>Verification is bundle-scoped, not document-scoped: a bundle is the evidentiary set the notary
 * signs against, and it is the unit a verification covers. The trigger is therefore the bundle being
 * locked, not any single document's OCR review finishing — that is why this carries a {@code bundleId}
 * and not a {@code documentId}.
 *
 * <p>Lives in {@code notarist-core} so notarist-case can announce the bundle-ready fact without
 * notarist-verification depending on the case module's domain events.
 *
 * @param bundleId    the sealed bundle to verify
 * @param tenantId    the owning tenant — the RLS identity the consumer must apply before writing
 * @param actorUserId the user (or system id) that locked the bundle; recorded as the acting identity
 */
public record VerificationProvisioningRequested(
        UUID bundleId,
        UUID tenantId,
        UUID actorUserId
) {
    public VerificationProvisioningRequested {
        if (bundleId == null) throw new IllegalArgumentException("bundleId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
    }
}
