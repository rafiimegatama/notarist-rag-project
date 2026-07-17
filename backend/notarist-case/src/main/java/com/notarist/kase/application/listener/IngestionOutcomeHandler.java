package com.notarist.kase.application.listener;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.application.port.in.DocumentIngestionOutcome;
import com.notarist.kase.application.port.in.HandleIngestionOutcomeUseCase;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.infrastructure.observability.CaseMetrics;
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
 * <p>It maintains the same invariants a manual case transition does: the transition, an append to the
 * case's append-only timeline, the domain events, and the transition metric all happen together in the
 * driving listener's transaction — so an automatic (ingestion-driven) advance is indistinguishable in
 * the timeline, the dashboard and the metrics from one a human took.
 *
 * <p>Wired via {@code DocumentIngestionCompletedListener}, which consumes the pipeline's shared
 * {@code DocumentIngestionCompleted} core event, resolves the owning case+bundle from the document id
 * through the bundle composition table (no caseId is ever carried on the ingest job — see
 * {@code BundleRepository#findByDocumentId}), and calls {@link #handle}. The pipeline therefore stays
 * ignorant of cases, and the Case module resolves its own ownership.
 *
 * <p>Deliberately framework-free (it takes only ports) and registered in {@code CaseModuleConfig};
 * the transaction boundary is owned by the listener that drives it, so {@code handle} runs inside the
 * listener's transaction.
 */
public class IngestionOutcomeHandler implements HandleIngestionOutcomeUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestionOutcomeHandler.class);

    private final CaseRepository caseRepository;
    private final TimelineRepository timelineRepository;
    private final DomainEventPublisher eventPublisher;
    private final CaseMetrics metrics;

    public IngestionOutcomeHandler(CaseRepository caseRepository,
                                   TimelineRepository timelineRepository,
                                   DomainEventPublisher eventPublisher,
                                   CaseMetrics metrics) {
        this.caseRepository = caseRepository;
        this.timelineRepository = timelineRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
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

        CaseState from = aCase.state();
        CaseState target = outcome.succeeded() ? CaseState.FIELD_EXTRACTION : CaseState.OCR_FAILED;
        // System-initiated, so no human caller supplies these — mint fresh ids for the audit chain.
        CorrelationId correlationId = CorrelationId.generate();
        TraceId traceId = TraceId.generate();

        aCase.transition(target, Actor.system());
        CaseState to = aCase.state();

        // Same append-only story a manual transition writes — a case is always created with a timeline.
        Timeline timeline = timelineRepository.findByCase(aCase.caseId())
                .orElseThrow(() -> new InvariantViolationException(
                        "Case " + aCase.caseId() + " has no timeline — a case is always created with one"));
        timeline.append(TimelineEntryType.STATE_CHANGED,
                "OCR pipeline " + (outcome.succeeded() ? "completed" : "failed")
                        + " for document " + outcome.documentId().value() + " — " + from + " → " + to,
                Actor.system(), correlationId, traceId);

        caseRepository.save(aCase);
        timelineRepository.save(timeline);

        eventPublisher.publishAll(aCase.pullDomainEvents());
        eventPublisher.publishAll(timeline.pullDomainEvents());

        metrics.recordTransition(from.name(), to.name(), "FORWARD");

        log.info("Case {} advanced {} → {} on ingestion outcome for document {}",
                aCase.caseId(), from, to, outcome.documentId());
    }
}
