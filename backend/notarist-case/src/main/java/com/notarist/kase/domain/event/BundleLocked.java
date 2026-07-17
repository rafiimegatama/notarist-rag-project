package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.*;

import java.util.List;
import java.util.UUID;

/**
 * The bundle was sealed. Carries the exact document set relied upon, because that set is the
 * evidentiary basis of whatever the notary signs next — and it must be reconstructible forever.
 */
public final class BundleLocked extends CaseDomainEvent {

    private final BundleId bundleId;
    private final CaseId caseId;
    private final List<DocumentId> documentIds;
    private final UUID tenantId;
    private final Actor actor;

    public BundleLocked(BundleId bundleId, CaseId caseId, List<DocumentId> documentIds,
                        UUID tenantId, Actor actor, CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.documentIds = List.copyOf(documentIds);
        this.tenantId = tenantId;
        this.actor = actor;
    }

    @Override public String eventType() { return "BundleLocked"; }

    public BundleId bundleId()           { return bundleId; }
    public CaseId caseId()               { return caseId; }
    public List<DocumentId> documentIds(){ return documentIds; }
    public UUID tenantId()               { return tenantId; }
    public Actor actor()                 { return actor; }
}
