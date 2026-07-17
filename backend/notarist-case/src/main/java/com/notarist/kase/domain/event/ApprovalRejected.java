package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.ApprovalType;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/** Rejected, with a mandatory reason. The reason is a legally significant fact, not a courtesy. */
public final class ApprovalRejected extends CaseDomainEvent {

    private final ApprovalId approvalId;
    private final CaseId caseId;
    private final ApprovalType approvalType;
    private final Actor decidedBy;
    private final String reason;
    private final UUID tenantId;

    public ApprovalRejected(ApprovalId approvalId, CaseId caseId, ApprovalType approvalType,
                            Actor decidedBy, String reason, UUID tenantId,
                            CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.approvalId = approvalId;
        this.caseId = caseId;
        this.approvalType = approvalType;
        this.decidedBy = decidedBy;
        this.reason = reason;
        this.tenantId = tenantId;
    }

    @Override public String eventType() { return "ApprovalRejected"; }

    public ApprovalId approvalId()     { return approvalId; }
    public CaseId caseId()             { return caseId; }
    public ApprovalType approvalType() { return approvalType; }
    public Actor decidedBy()           { return decidedBy; }
    public String reason()             { return reason; }
    public UUID tenantId()             { return tenantId; }
}
