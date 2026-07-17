package com.notarist.review.infrastructure.event;

import com.notarist.core.api.event.OcrReviewProvisioningRequested;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.review.application.command.InitializeReviewCommand;
import com.notarist.review.application.port.in.OcrReviewProvisioningUseCase;
import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * Provisions the OCR-review landing rows automatically when the ingestion pipeline reports that a
 * document's OCR stage completed. This is the production caller the {@link OcrReviewProvisioningUseCase}
 * was built to have — before this, {@code initializeReview} had no runtime driver at all.
 *
 * <h3>Why AFTER_COMMIT + REQUIRES_NEW</h3>
 * The event is published from inside the ingest pipeline transaction. Consuming it at
 * {@link TransactionPhase#AFTER_COMMIT} means a review is created only once the OCR stage has durably
 * committed; a stage that rolls back or is dead-lettered provisions nothing. At this phase the
 * publishing transaction has already committed but its synchronizations are still unwinding, so a plain
 * {@code REQUIRED} write would join a completing transaction and never commit — a real, DB-verified
 * failure mode. This method therefore opens its own {@code REQUIRES_NEW} transaction, which the
 * {@code initializeReview} call runs inside.
 *
 * <h3>Why it sets the tenant identity itself</h3>
 * This runs on the pipeline's thread, not an HTTP request thread, so no {@link VpdContextHolder}
 * principal is present. The {@code ocr_review} table is under fail-closed RLS (Flyway V9/V14, FORCE),
 * so an insert with no tenant identity set is silently rejected by the policy's WITH CHECK. Rather than
 * open a system-wide RLS bypass on the review table, this establishes the identity of the event's own
 * tenant — the exact tenant that owns the document — so the standard tenant policy accepts the write
 * and no cross-tenant surface is created. The context is always cleared, so it never leaks to the next
 * task on this thread.
 *
 * <h3>Idempotency</h3>
 * {@code initializeReview} rejects a duplicate with {@link ReviewInvariantViolationException}. A
 * redelivered event or an OCR re-run is therefore a no-op, not an error.
 */
@Component
public class OcrReviewProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(OcrReviewProvisioningListener.class);

    private static final String SYSTEM_ROLE = "SYSTEM";

    private final OcrReviewProvisioningUseCase provisioningUseCase;

    public OcrReviewProvisioningListener(OcrReviewProvisioningUseCase provisioningUseCase) {
        this.provisioningUseCase = provisioningUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOcrReviewProvisioningRequested(OcrReviewProvisioningRequested event) {
        Optional<VpdContextHolder.VpdPrincipal> previous = VpdContextHolder.get();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                event.uploadedBy(), event.tenantId(), SYSTEM_ROLE));
        try {
            provisioningUseCase.initializeReview(new InitializeReviewCommand(
                    event.documentId(),
                    event.tenantId(),
                    event.documentName(),
                    event.pageCount(),
                    false,   // stampDetected — OCR does not assert this; the reviewer confirms it
                    false,   // signatureDetected — likewise a manual finding
                    event.overallConfidence(),
                    List.of(),   // fields — the human reviewer populates these
                    List.of(),   // authorityItems — likewise
                    null));      // no HTTP caller; this is a system-initiated provisioning
            log.info("Provisioned OCR review for document {} (tenant {})",
                    event.documentId(), event.tenantId());
        } catch (ReviewInvariantViolationException alreadyExists) {
            log.debug("OCR review already exists for document {} — provisioning skipped (idempotent)",
                    event.documentId());
        } catch (RuntimeException ex) {
            // Loud, but non-fatal: the OCR stage has already committed, so failing here must not
            // pretend OCR failed. A human can re-drive provisioning; the pipeline keeps moving.
            log.error("Failed to provision OCR review for document {}: {}",
                    event.documentId(), ex.getMessage(), ex);
        } finally {
            previous.ifPresentOrElse(VpdContextHolder::set, VpdContextHolder::clear);
        }
    }
}
