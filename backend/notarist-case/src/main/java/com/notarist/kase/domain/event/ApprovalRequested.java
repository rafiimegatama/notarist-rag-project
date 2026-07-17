package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.ApprovalType;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/** A decision is now waiting on a ROLE (not a person). Consumed by Notification and Reminder. */
public final class ApprovalRequested extends CaseDomainEvent {

    private final ApprovalId approvalId;
    private final CaseId caseId;
    private final ApprovalType approvalType;
    private final Role requiredRole;
    private final UUID tenantId;

    public ApprovalRequested(ApprovalId approvalId, CaseId caseId, ApprovalType approvalType,
                             Role requiredRole, UUID tenantId,
                             CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.approvalId = approvalId;
        this.caseId = caseId;
        this.approvalType = approvalType;
        this.requiredRole = requiredRole;
        this.tenantId = tenantId;
    }

    @Override public String eventType() { return "ApprovalRequested"; }

    public ApprovalId approvalId()     { return approvalId; }
    public CaseId caseId()             { return caseId; }
    public ApprovalType approvalType() { return approvalType; }
    public Role requiredRole()         { return requiredRole; }
    public UUID tenantId()             { return tenantId; }
}
