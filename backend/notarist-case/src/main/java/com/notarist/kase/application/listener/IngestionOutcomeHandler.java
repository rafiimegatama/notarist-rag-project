package com.notarist.kase.application.listener;

import com.notarist.kase.application.port.in.DocumentIngestionOutcome;
import com.notarist.kase.application.port.in.HandleIngestionOutcomeUseCase;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Advances a Case when the ingestion pipeline reports a document finished.
 *
 * <p>It calls {@code aCase.transition(...)} — it does NOT touch a field. No service in this codebase
 * may mutate aggregate state directly; the aggregate owns its rules, and this class owns only the
 * decision about <em>which</em> lawful transition to request.
 *
 * <p>Note what it does not do: it does not ask the ingest module anything, and it does not count
 * documents on the pipeline's behalf. Whether the bundle is now complete is the Bundle's business.
 *
 * <p>⚠️ NOT WIRED YET. The composition root must translate the ingest module's completion event into
 * {@link DocumentIngestionOutcome}, which requires the pipeline to echo back the caseId it was handed
 * in its job payload. That is a one-line change to notarist-ingest and is explicitly out of scope this
 * sprint (the upload pipeline must not be modified). Until then this handler is exercised by tests
 * only — and it is deliberately written now so the boundary exists before anyone is tempted to have a
 * worker reach into a Case directly.
 */
public class IngestionOutcomeHandler implements HandleIngestionOutcomeUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestionOutcomeHandler.class);

    private final CaseRepository caseRepository;
    private final DomainEventPublisher eventPublisher;

    public IngestionOutcomeHandler(CaseRepository caseRepository, DomainEventPublisher eventPublisher) {
        this.caseRepository = caseRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(DocumentIngestionOutcome outcome) {
        Optional<Case> found = caseRepository.findById(outcome.caseId());
        if (found.isEmpty()) {
            // A document with no case is normal — thousands already exist. Not an error.
            log.debug("Ingestion outcome for unknown case {} — ignoring", outcome.caseId());
            return;
        }

        Case aCase = found.get();

        // Idempotent: at-least-once delivery means seeing the same completion twice is normal, and a
        // case that has already moved on must not be dragged backwards by a redelivered event.
        if (aCase.state() != CaseState.OCR_RUNNING) {
            log.debug("Case {} is {} — ingestion outcome ignored (not awaiting OCR)",
                    aCase.caseId(), aCase.state());
            return;
        }

        CaseState target = outcome.succeeded() ? CaseState.FIELD_EXTRACTION : CaseState.OCR_FAILED;
        aCase.transition(target, Actor.system());

        caseRepository.save(aCase);
        eventPublisher.publishAll(aCase.pullDomainEvents());

        log.info("Case {} advanced to {} on ingestion outcome for document {}",
                aCase.caseId(), target, outcome.documentId());
    }
}
