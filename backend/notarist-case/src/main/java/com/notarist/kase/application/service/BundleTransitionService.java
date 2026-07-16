package com.notarist.kase.application.service;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.api.response.BundleResponse;
import com.notarist.kase.application.command.ChangeBundleStatusCommand;
import com.notarist.kase.application.port.out.BundleRepository;
import com.notarist.kase.application.port.out.BundleTimelineRepository;
import com.notarist.kase.application.port.out.BundleWorkflowRepository;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.exception.BundleNotFoundException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.model.BundleTimeline;
import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.infrastructure.event.BundleAuditEventPublisher;
import com.notarist.kase.infrastructure.observability.BundleMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Drives a bundle's workflow transition, including the cross-aggregate DELIVERY guard the aggregate
 * cannot enforce alone.
 *
 * <p><b>Bundle cannot DELIVER until:</b> it has passed QC (structural — READY_FOR_DELIVERY is only
 * reachable through QC_PASSED, so a delivered bundle has necessarily passed QC and left verification),
 * and its owning Case is APPROVED (the check this service adds, reading the Case aggregate). The
 * legality of the edge itself and who may take it are decided by {@link BundleWorkflow#transition} —
 * this service never consults the state machine.
 */
@Service
@Transactional
public class BundleTransitionService {

    /** A Case is "approved" for delivery once the notary has finalized it (or beyond). */
    private static final Set<CaseState> CASE_APPROVED =
            Set.of(CaseState.FINALIZED, CaseState.DELIVERED, CaseState.ARCHIVED);

    private final BundleWorkflowRepository workflowRepository;
    private final BundleTimelineRepository timelineRepository;
    private final BundleRepository bundleRepository;
    private final CaseRepository caseRepository;
    private final DomainEventPublisher eventPublisher;
    private final BundleAuditEventPublisher auditPublisher;
    private final BundleMetrics metrics;

    public BundleTransitionService(BundleWorkflowRepository workflowRepository,
                                   BundleTimelineRepository timelineRepository,
                                   BundleRepository bundleRepository,
                                   CaseRepository caseRepository,
                                   DomainEventPublisher eventPublisher,
                                   BundleAuditEventPublisher auditPublisher,
                                   BundleMetrics metrics) {
        this.workflowRepository = workflowRepository;
        this.timelineRepository = timelineRepository;
        this.bundleRepository = bundleRepository;
        this.caseRepository = caseRepository;
        this.eventPublisher = eventPublisher;
        this.auditPublisher = auditPublisher;
        this.metrics = metrics;
    }

    public BundleResponse changeStatus(ChangeBundleStatusCommand command) {
        return metrics.changeStatusTimer().record(() -> doChangeStatus(command));
    }

    private BundleResponse doChangeStatus(ChangeBundleStatusCommand command) {
        CallerContext caller = command.caller();
        BundleWorkflow workflow = loadForCaller(command.bundleId().value(), caller);

        BundleWorkflowStatus from = workflow.status();
        BundleWorkflowStatus target = command.targetStatus();
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        if (target == BundleWorkflowStatus.DELIVERED) {
            enforceDeliveryGuard(workflow);
        }

        // The aggregate decides legality and authority; any failure throws a domain exception here.
        workflow.transition(target, caller.asActor(), correlationId, traceId);
        BundleWorkflowStatus to = workflow.status();

        BundleTimeline timeline = timelineRepository.findByBundle(command.bundleId())
                .orElseThrow(() -> new InvariantViolationException(
                        "Bundle " + command.bundleId().value() + " has no timeline"));
        timeline.append(timelineTypeFor(to), "Status " + from + " → " + to, caller.asActor());
        if (workflow.isTerminal() && timeline.status().acceptsEntries()) {
            timeline.seal();
        }

        workflowRepository.save(workflow);
        timelineRepository.save(timeline);
        eventPublisher.publishAll(workflow.pullDomainEvents());

        auditPublisher.publishStatusChanged(
                workflow.bundleId().value(), caller.userId(), caller.role().name(),
                caller.tenantId(), from.name(), to.name(), correlationId);
        metrics.recordTransition(from.name(), to.name());

        Bundle bundle = bundleRepository.findById(command.bundleId())
                .orElseThrow(() -> new BundleNotFoundException(
                        "Bundle not found: " + command.bundleId().value()));
        return BundleResponse.from(bundle, workflow);
    }

    /**
     * DELIVERY precondition: the case must be approved. QC-passed and verification-complete are
     * guaranteed structurally by the state machine (DELIVERED ⇐ READY_FOR_DELIVERY ⇐ QC_PASSED).
     */
    private void enforceDeliveryGuard(BundleWorkflow workflow) {
        if (!workflow.status().hasPassedQc()) {
            throw new InvariantViolationException(
                    "Bundle " + workflow.bundleId().value() + " cannot be delivered before it passes QC");
        }
        Case aCase = caseRepository.findById(workflow.caseId())
                .orElseThrow(() -> new InvariantViolationException(
                        "Bundle " + workflow.bundleId().value() + " references a missing case"));
        if (!CASE_APPROVED.contains(aCase.state())) {
            throw new InvariantViolationException(
                    "Bundle cannot be delivered until its case is approved (case is " + aCase.state() + ")");
        }
    }

    private BundleWorkflow loadForCaller(java.util.UUID bundleUuid, CallerContext caller) {
        com.notarist.kase.domain.valueobject.BundleId bundleId =
                com.notarist.kase.domain.valueobject.BundleId.of(bundleUuid);
        BundleWorkflow workflow = workflowRepository.findById(bundleId)
                .orElseThrow(() -> new BundleNotFoundException("Bundle not found: " + bundleUuid));
        if (!workflow.tenantId().equals(caller.tenantId())) {
            auditPublisher.publishAccessDenied(bundleUuid, caller.userId(), caller.role().name(),
                    caller.tenantId(), "CROSS_TENANT_ACCESS", caller.correlationId());
            throw new BundleNotFoundException("Bundle not found: " + bundleUuid);
        }
        return workflow;
    }

    private TimelineEntryType timelineTypeFor(BundleWorkflowStatus status) {
        return switch (status) {
            case READY_FOR_VERIFICATION, UNDER_VERIFICATION -> TimelineEntryType.VERIFICATION;
            case READY_FOR_QC, QC_PASSED, QC_FAILED -> TimelineEntryType.QC;
            case DELIVERED -> TimelineEntryType.DELIVERY;
            case LOCKED -> TimelineEntryType.BUNDLE_LOCKED;
            default -> TimelineEntryType.STATE_CHANGED;
        };
    }
}
