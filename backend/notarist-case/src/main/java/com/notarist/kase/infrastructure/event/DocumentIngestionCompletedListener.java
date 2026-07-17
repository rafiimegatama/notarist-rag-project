package com.notarist.kase.infrastructure.event;

import com.notarist.core.api.event.DocumentIngestionCompleted;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.application.port.in.DocumentIngestionOutcome;
import com.notarist.kase.application.port.in.HandleIngestionOutcomeUseCase;
import com.notarist.kase.application.port.out.BundleRepository;
import com.notarist.kase.domain.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * The composition seam between the ingestion pipeline and the Case context. It consumes the shared
 * {@link DocumentIngestionCompleted} core event and hands the Case module a
 * {@link DocumentIngestionOutcome} in its own language.
 *
 * <p><b>Resolution, not a carried id.</b> The pipeline never knows which case a document belongs to,
 * and the upload contract carries no caseId. This listener resolves the owning case+bundle from the
 * document id through the existing bundle composition table
 * ({@link BundleRepository#findByDocumentId}). A document that belongs to no bundle — the common case,
 * a standalone upload — resolves to nothing and is silently ignored; that is not an error.
 *
 * <p><b>AFTER_COMMIT.</b> The event is published inside the pipeline transaction, so consuming it at
 * {@link TransactionPhase#AFTER_COMMIT} means the Case only ever reacts to ingestion states that
 * actually committed. There is no ambient transaction at that phase; the repository and the handler
 * each open their own.
 *
 * <p><b>Tenant identity.</b> Runs on the pipeline thread with no principal, and both the resolution
 * read and the case write go through fail-closed RLS. It installs the event's own tenant identity for
 * the duration and restores whatever was there afterwards, so nothing leaks across pooled threads and
 * no system-wide RLS bypass is opened on case tables.
 */
@Component
public class DocumentIngestionCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionCompletedListener.class);

    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final java.util.UUID SYSTEM_USER = new java.util.UUID(0L, 0L);

    private final BundleRepository bundleRepository;
    private final HandleIngestionOutcomeUseCase handleIngestionOutcome;

    public DocumentIngestionCompletedListener(BundleRepository bundleRepository,
                                              HandleIngestionOutcomeUseCase handleIngestionOutcome) {
        this.bundleRepository = bundleRepository;
        this.handleIngestionOutcome = handleIngestionOutcome;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentIngestionCompleted(DocumentIngestionCompleted event) {
        Optional<VpdContextHolder.VpdPrincipal> previous = VpdContextHolder.get();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                SYSTEM_USER, event.tenantId(), SYSTEM_ROLE));
        try {
            DocumentId documentId = new DocumentId(event.documentId());
            Optional<Bundle> bundle = bundleRepository.findByDocumentId(documentId);
            if (bundle.isEmpty()) {
                log.debug("Document {} belongs to no bundle — ingestion outcome not routed to any case",
                        event.documentId());
                return;
            }

            Bundle b = bundle.get();
            handleIngestionOutcome.handle(new DocumentIngestionOutcome(
                    documentId, b.caseId(), b.bundleId(), event.succeeded()));
            log.info("Routed ingestion outcome (succeeded={}) for document {} to case {}",
                    event.succeeded(), event.documentId(), b.caseId());
        } catch (RuntimeException ex) {
            // Ingestion already committed; a failure to advance the case must not masquerade as an
            // ingestion failure. Loud, but non-fatal — the case can be advanced by hand.
            log.error("Failed to route ingestion outcome for document {}: {}",
                    event.documentId(), ex.getMessage(), ex);
        } finally {
            previous.ifPresentOrElse(VpdContextHolder::set, VpdContextHolder::clear);
        }
    }
}
