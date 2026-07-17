package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.ApprovalType;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/**
 * A human with the required authority approved.
 *
 * <p>{@code ApprovalGranted(NOTARY_SIGNATURE)} is the most legally significant event in the system:
 * it is what causes a Case to become FINALIZED and a repertorium number to be allocated. Idempotency
 * key is the approvalId — an approval decides exactly once, so a redelivered event is a no-op.
 */
public final class ApprovalGranted extends CaseDomainEvent {

    private final ApprovalId approvalId;
    private final CaseId caseId;
    private final ApprovalType approvalType;
    private final Actor decidedBy;
    private final UUID tenantId;

    public ApprovalGranted(ApprovalId approvalId, CaseId caseId, ApprovalType approvalType,
                           Actor decidedBy, UUID tenantId,
                           CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.approvalId = approvalId;
        this.caseId = caseId;
        this.approvalType = approvalType;
        this.decidedBy = decidedBy;
        this.tenantId = tenantId;
    }

    @Override public String eventType() { return "ApprovalGranted"; }

    public ApprovalId approvalId()     { return approvalId; }
    public CaseId caseId()             { return caseId; }
    public ApprovalType approvalType() { return approvalType; }
    public Actor decidedBy()           { return decidedBy; }
    public UUID tenantId()             { return tenantId; }
}
