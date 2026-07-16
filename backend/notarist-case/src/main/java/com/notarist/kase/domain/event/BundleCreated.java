package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

public final class BundleCreated extends CaseDomainEvent {

    private final BundleId bundleId;
    private final CaseId caseId;
    private final BundleType bundleType;
    private final int expectedDocumentCount;
    private final UUID tenantId;
    private final Actor actor;

    public BundleCreated(BundleId bundleId, CaseId caseId, BundleType bundleType,
                         int expectedDocumentCount, UUID tenantId, Actor actor,
                         CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.bundleType = bundleType;
        this.expectedDocumentCount = expectedDocumentCount;
        this.tenantId = tenantId;
        this.actor = actor;
    }

    @Override public String eventType() { return "BundleCreated"; }

    public BundleId bundleId()          { return bundleId; }
    public CaseId caseId()              { return caseId; }
    public BundleType bundleType()      { return bundleType; }
    public int expectedDocumentCount()  { return expectedDocumentCount; }
    public UUID tenantId()              { return tenantId; }
    public Actor actor()                { return actor; }
}
