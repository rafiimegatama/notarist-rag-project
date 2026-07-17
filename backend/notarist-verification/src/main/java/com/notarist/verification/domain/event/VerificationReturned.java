package com.notarist.verification.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.VerificationId;

import java.util.UUID;

/** Raised when an outcome (VERIFIED/FAILED) is returned to UNDER_VERIFICATION for rework. */
public final class VerificationReturned extends VerificationDomainEvent {

    private final VerificationId verificationId;
    private final BundleId bundleId;
    private final UUID tenantId;
    private final VerificationStatus fromStatus;
    private final UUID reviewerId;

    public VerificationReturned(VerificationId verificationId, BundleId bundleId, UUID tenantId,
                                VerificationStatus fromStatus, UUID reviewerId,
                                CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.verificationId = verificationId;
        this.bundleId = bundleId;
        this.tenantId = tenantId;
        this.fromStatus = fromStatus;
        this.reviewerId = reviewerId;
    }

    @Override public String eventType() { return "VERIFICATION_RETURNED"; }

    public VerificationId verificationId() { return verificationId; }
    public BundleId bundleId()             { return bundleId; }
    public UUID tenantId()                 { return tenantId; }
    public VerificationStatus fromStatus() { return fromStatus; }
    public UUID reviewerId()               { return reviewerId; }
}
