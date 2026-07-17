package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.NomorAkta;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.RepertoriumId;

import java.util.UUID;

/**
 * A statutory akta number was allocated from the notary's register.
 *
 * <p>⚠️ This event must NOT be retried automatically. A retry loop around statutory number allocation
 * is precisely how a gap or a duplicate is manufactured — and a gap in the repertorium is a regulatory
 * finding, not a bug report. A failure here pages a human.
 *
 * <p>Idempotency key is the caseId: allocation is once-only per case, enforced inside the aggregate.
 */
public final class RepertoriumNumberAllocated extends CaseDomainEvent {

    private final RepertoriumId repertoriumId;
    private final CaseId caseId;
    private final NomorAkta nomorAkta;
    private final int sequence;
    private final int year;
    private final UUID notarisId;
    private final UUID tenantId;

    public RepertoriumNumberAllocated(RepertoriumId repertoriumId, CaseId caseId, NomorAkta nomorAkta,
                                      int sequence, int year, UUID notarisId, UUID tenantId,
                                      CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.repertoriumId = repertoriumId;
        this.caseId = caseId;
        this.nomorAkta = nomorAkta;
        this.sequence = sequence;
        this.year = year;
        this.notarisId = notarisId;
        this.tenantId = tenantId;
    }

    @Override public String eventType() { return "RepertoriumNumberAllocated"; }

    public RepertoriumId repertoriumId() { return repertoriumId; }
    public CaseId caseId()               { return caseId; }
    public NomorAkta nomorAkta()         { return nomorAkta; }
    public int sequence()                { return sequence; }
    public int year()                    { return year; }
    public UUID notarisId()              { return notarisId; }
    public UUID tenantId()               { return tenantId; }
}
