package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

import java.util.UUID;

/** A bundle moved through its process workflow. */
public final class BundleWorkflowTransitioned extends CaseDomainEvent {

    private final BundleId bundleId;
    private final CaseId caseId;
    private final BundleWorkflowStatus fromStatus;
    private final BundleWorkflowStatus toStatus;
    private final UUID tenantId;
    private final Actor actor;

    public BundleWorkflowTransitioned(BundleId bundleId, CaseId caseId,
                                      BundleWorkflowStatus fromStatus, BundleWorkflowStatus toStatus,
                                      UUID tenantId, Actor actor,
                                      CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.tenantId = tenantId;
        this.actor = actor;
    }

    @Override public String eventType() { return "BundleWorkflowTransitioned"; }

    public BundleId bundleId()               { return bundleId; }
    public CaseId caseId()                   { return caseId; }
    public BundleWorkflowStatus fromStatus() { return fromStatus; }
    public BundleWorkflowStatus toStatus()   { return toStatus; }
    public UUID tenantId()                   { return tenantId; }
    public Actor actor()                     { return actor; }
}
