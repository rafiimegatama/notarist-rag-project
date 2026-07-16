package com.notarist.verification.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.VerificationId;

import java.util.UUID;

/** Raised when the last checklist item receives a decision — the whole checklist is now decided. */
public final class ChecklistCompleted extends VerificationDomainEvent {

    private final VerificationId verificationId;
    private final BundleId bundleId;
    private final UUID tenantId;
    private final UUID reviewerId;

    public ChecklistCompleted(VerificationId verificationId, BundleId bundleId, UUID tenantId,
                              UUID reviewerId, CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.verificationId = verificationId;
        this.bundleId = bundleId;
        this.tenantId = tenantId;
        this.reviewerId = reviewerId;
    }

    @Override public String eventType() { return "VERIFICATION_CHECKLIST_COMPLETED"; }

    public VerificationId verificationId() { return verificationId; }
    public BundleId bundleId()             { return bundleId; }
    public UUID tenantId()                 { return tenantId; }
    public UUID reviewerId()               { return reviewerId; }
}
