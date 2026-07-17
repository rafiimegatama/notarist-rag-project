package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.TimelineId;

import java.util.UUID;

/** The case reached a terminal state; its story can no longer grow a new chapter. */
public final class TimelineSealed extends CaseDomainEvent {

    private final TimelineId timelineId;
    private final CaseId caseId;
    private final int entryCount;
    private final UUID tenantId;

    public TimelineSealed(TimelineId timelineId, CaseId caseId, int entryCount, UUID tenantId,
                          CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.timelineId = timelineId;
        this.caseId = caseId;
        this.entryCount = entryCount;
        this.tenantId = tenantId;
    }

    @Override public String eventType() { return "TimelineSealed"; }

    public TimelineId timelineId() { return timelineId; }
    public CaseId caseId()         { return caseId; }
    public int entryCount()        { return entryCount; }
    public UUID tenantId()         { return tenantId; }
}
