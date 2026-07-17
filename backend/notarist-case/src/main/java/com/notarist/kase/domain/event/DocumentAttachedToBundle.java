package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/**
 * A document (which lives in the Document aggregate) was linked into a bundle.
 *
 * <p>Idempotency key is the natural pair (bundleId, documentId): re-attaching the same document is a
 * no-op, not an error, so a redelivered event cannot double-attach.
 */
public final class DocumentAttachedToBundle extends CaseDomainEvent {

    private final BundleId bundleId;
    private final CaseId caseId;
    private final DocumentId documentId;
    private final String roleInBundle;
    private final UUID tenantId;
    private final Actor actor;

    public DocumentAttachedToBundle(BundleId bundleId, CaseId caseId, DocumentId documentId,
                                    String roleInBundle, UUID tenantId, Actor actor,
                                    CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.documentId = documentId;
        this.roleInBundle = roleInBundle;
        this.tenantId = tenantId;
        this.actor = actor;
    }

    @Override public String eventType() { return "DocumentAttachedToBundle"; }

    public BundleId bundleId()     { return bundleId; }
    public CaseId caseId()         { return caseId; }
    public DocumentId documentId() { return documentId; }
    public String roleInBundle()   { return roleInBundle; }
    public UUID tenantId()         { return tenantId; }
    public Actor actor()           { return actor; }
}
