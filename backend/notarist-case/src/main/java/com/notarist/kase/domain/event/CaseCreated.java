package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/** A new unit of notarial work was opened. */
public final class CaseCreated extends CaseDomainEvent {

    private final CaseId caseId;
    private final CaseNumber caseNumber;
    private final CaseType caseType;
    private final UUID tenantId;
    private final Actor actor;

    public CaseCreated(CaseId caseId, CaseNumber caseNumber, CaseType caseType,
                       UUID tenantId, Actor actor, CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.caseId = caseId;
        this.caseNumber = caseNumber;
        this.caseType = caseType;
        this.tenantId = tenantId;
        this.actor = actor;
    }

    @Override public String eventType() { return "CaseCreated"; }

    public CaseId caseId()         { return caseId; }
    public CaseNumber caseNumber() { return caseNumber; }
    public CaseType caseType()     { return caseType; }
    public UUID tenantId()         { return tenantId; }
    public Actor actor()           { return actor; }
}
