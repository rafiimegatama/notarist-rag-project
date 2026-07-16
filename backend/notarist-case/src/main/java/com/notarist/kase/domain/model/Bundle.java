package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.BundleCreated;
import com.notarist.kase.domain.event.BundleLocked;
import com.notarist.kase.domain.event.DocumentAttachedToBundle;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.state.BundleStateMachine;
import com.notarist.kase.domain.state.BundleStatus;
import com.notarist.kase.domain.valueobject.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for a group of documents serving one purpose within a case.
 *
 * <p><b>It holds {@link DocumentRef} — IDs — never {@code DocumentLegal} objects.</b> The Document
 * aggregate is reused, not duplicated and not owned. This is what lets the same physical document
 * (one client's KTP) be referenced by many bundles without copying the blob or re-running OCR.
 *
 * <p><b>LOCKED is irreversible, at every privilege level.</b> There is no {@code unlock()} on this
 * class — not a guarded one, not an admin one. The notary signed on the basis of these exact
 * documents; swapping one afterwards would silently invalidate the evidentiary chain. A correction
 * means a NEW bundle, recorded as such.
 */
public class Bundle extends AggregateRoot {

    private final BundleId bundleId;
    private final CaseId caseId;
    private final BundleType bundleType;
    private final UUID tenantId;
    private final int expectedDocumentCount;
    private final Instant createdAt;

    private BundleStatus status;
    private final List<DocumentRef> documents = new ArrayList<>();

    private Bundle(BundleId bundleId, CaseId caseId, BundleType bundleType, UUID tenantId,
                   int expectedDocumentCount, BundleStatus status, Instant createdAt) {
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.bundleType = bundleType;
        this.tenantId = tenantId;
        this.expectedDocumentCount = expectedDocumentCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Bundle open(BundleId bundleId, CaseId caseId, BundleType bundleType, UUID tenantId,
                              int expectedDocumentCount, Actor actor,
                              CorrelationId correlationId, TraceId traceId) {

        if (expectedDocumentCount < 0) {
            throw new InvariantViolationException("expectedDocumentCount must not be negative");
        }

        Bundle b = new Bundle(bundleId, caseId, bundleType, tenantId, expectedDocumentCount,
                BundleStatus.OPEN, Instant.now());
        b.validate();
        b.raise(new BundleCreated(bundleId, caseId, bundleType, expectedDocumentCount,
                tenantId, actor, correlationId, traceId));
        return b;
    }

    public static Bundle rehydrate(BundleId bundleId, CaseId caseId, BundleType bundleType,
                                   UUID tenantId, int expectedDocumentCount, BundleStatus status,
                                   List<DocumentRef> documents, Instant createdAt) {
        Bundle b = new Bundle(bundleId, caseId, bundleType, tenantId, expectedDocumentCount,
                status, createdAt);
        if (documents != null) b.documents.addAll(documents);
        return b;
    }

    // ---- State ---------------------------------------------------------------------------------

    /** The only way status changes. */
    public void transition(BundleStatus target, Actor actor) {
        if (!BundleStateMachine.isLegal(status, target)) {
            throw IllegalTransitionException.of(this, status, target);
        }
        // COMPLETE is a statement of fact about the contents, not a wish.
        if (target == BundleStatus.COMPLETE && !isComplete()) {
            throw new InvariantViolationException(
                    "Bundle " + bundleId + " has " + documents.size() + " of "
                            + expectedDocumentCount + " expected documents — it is not COMPLETE");
        }
        this.status = target;
        enforceInvariants();
    }

    public void attachDocument(DocumentRef ref, Actor actor,
                               CorrelationId correlationId, TraceId traceId) {
        if (ref == null) throw new InvariantViolationException("DocumentRef must not be null");
        if (!status.acceptsDocuments()) {
            throw new IllegalTransitionException(
                    "Bundle " + bundleId + " is " + status + " — it no longer accepts documents");
        }
        // Idempotent: re-attaching the same document is a no-op, not an error. At-least-once event
        // delivery makes duplicate attachment normal, not exceptional.
        if (containsDocument(ref.documentId())) return;

        documents.add(ref);
        enforceInvariants();
        raise(new DocumentAttachedToBundle(bundleId, caseId, ref.documentId(), ref.roleInBundle(),
                tenantId, actor, correlationId, traceId));
    }

    public void detachDocument(DocumentId documentId, Actor actor) {
        if (status == BundleStatus.LOCKED) {
            throw new IllegalTransitionException(
                    "Bundle " + bundleId + " is LOCKED — its document set is frozen. "
                            + "Correcting it requires a new bundle, not an edit.");
        }
        documents.removeIf(d -> d.documentId().equals(documentId));
        // Dropping below the expected count reopens the bundle — the state must not lie about it.
        if (status == BundleStatus.COMPLETE && !isComplete()) {
            this.status = BundleStatus.OPEN;
        }
        enforceInvariants();
    }

    /** Seals the bundle. Irreversible. Raises {@link BundleLocked} with the exact document set. */
    public void lock(Actor actor, CorrelationId correlationId, TraceId traceId) {
        transition(BundleStatus.LOCKED, actor);
        raise(new BundleLocked(bundleId, caseId,
                documents.stream().map(DocumentRef::documentId).toList(),
                tenantId, actor, correlationId, traceId));
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (bundleId == null)   throw new InvariantViolationException("bundleId is required");
        if (caseId == null)     throw new InvariantViolationException("caseId is required");
        if (bundleType == null) throw new InvariantViolationException("bundleType is required");
        if (tenantId == null)   throw new InvariantViolationException("tenantId is required");
        if (status == null)     throw new InvariantViolationException("status is required");

        if (status == BundleStatus.LOCKED && !isComplete()) {
            throw new InvariantViolationException(
                    "Bundle " + bundleId + " is LOCKED but incomplete — a notary cannot have relied "
                            + "on a document set that was never assembled");
        }
        long distinct = documents.stream().map(DocumentRef::documentId).distinct().count();
        if (distinct != documents.size()) {
            throw new InvariantViolationException("Bundle " + bundleId + " contains duplicate documents");
        }
    }

    // ---- Queries -------------------------------------------------------------------------------

    public boolean isComplete() {
        return documents.size() >= expectedDocumentCount;
    }

    public boolean containsDocument(DocumentId documentId) {
        return documents.stream().anyMatch(d -> d.documentId().equals(documentId));
    }

    public BundleId bundleId()            { return bundleId; }
    public CaseId caseId()                { return caseId; }
    public BundleType bundleType()        { return bundleType; }
    public UUID tenantId()                { return tenantId; }
    public BundleStatus status()          { return status; }
    public int expectedDocumentCount()    { return expectedDocumentCount; }
    public int documentCount()            { return documents.size(); }
    public Instant createdAt()            { return createdAt; }
    public List<DocumentRef> documents()  { return Collections.unmodifiableList(documents); }
}
