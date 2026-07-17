package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

public final class TimelineEntryAppended extends CaseDomainEvent {

    private final TimelineId timelineId;
    private final TimelineEntryId entryId;
    private final CaseId caseId;
    private final String entryType;
    private final String description;
    private final UUID tenantId;

    public TimelineEntryAppended(TimelineId timelineId, TimelineEntryId entryId, CaseId caseId,
                                 String entryType, String description, UUID tenantId,
                                 CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.timelineId = timelineId;
        this.entryId = entryId;
        this.caseId = caseId;
        this.entryType = entryType;
        this.description = description;
        this.tenantId = tenantId;
    }

    @Override public String eventType() { return "TimelineEntryAppended"; }

    public TimelineId timelineId()    { return timelineId; }
    public TimelineEntryId entryId()  { return entryId; }
    public CaseId caseId()            { return caseId; }
    public String entryType()         { return entryType; }
    public String description()       { return description; }
    public UUID tenantId()            { return tenantId; }
}
