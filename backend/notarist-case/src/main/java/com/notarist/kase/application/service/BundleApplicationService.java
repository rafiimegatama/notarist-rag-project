package com.notarist.kase.application.service;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.api.response.BundleResponse;
import com.notarist.kase.api.response.BundleTimelineResponse;
import com.notarist.kase.application.command.ChangeBundleStatusCommand;
import com.notarist.kase.application.command.OpenBundleCommand;
import com.notarist.kase.application.port.in.BundleManagementUseCase;
import com.notarist.kase.application.port.out.BundleRepository;
import com.notarist.kase.application.port.out.BundleTimelineRepository;
import com.notarist.kase.application.port.out.BundleWorkflowRepository;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.exception.BundleNotFoundException;
import com.notarist.kase.domain.exception.CaseNotFoundException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.factory.BundleFactory;
import com.notarist.kase.domain.factory.BundleWorkflowFactory;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.model.BundleTimeline;
import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.infrastructure.event.BundleAuditEventPublisher;
import com.notarist.kase.infrastructure.observability.BundleMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the Bundle use cases. Holds no rules of its own: it loads aggregates, asks them to do
 * the work ({@code BundleFactory.create}, {@code workflow.transition(...)}), persists the result in
 * one transaction, then publishes the events the aggregates raised. Status changes are delegated to
 * {@link BundleTransitionService}, which owns the cross-aggregate delivery guard.
 */
@Service
@Transactional
public class BundleApplicationService implements BundleManagementUseCase {

    private final CaseRepository caseRepository;
    private final BundleRepository bundleRepository;
    private final BundleWorkflowRepository workflowRepository;
    private final BundleTimelineRepository timelineRepository;
    private final BundleTransitionService transitionService;
    private final DomainEventPublisher eventPublisher;
    private final BundleAuditEventPublisher auditPublisher;
    private final BundleMetrics metrics;

    public BundleApplicationService(CaseRepository caseRepository,
                                    BundleRepository bundleRepository,
                                    BundleWorkflowRepository workflowRepository,
                                    BundleTimelineRepository timelineRepository,
                                    BundleTransitionService transitionService,
                                    DomainEventPublisher eventPublisher,
                                    BundleAuditEventPublisher auditPublisher,
                                    BundleMetrics metrics) {
        this.caseRepository = caseRepository;
        this.bundleRepository = bundleRepository;
        this.workflowRepository = workflowRepository;
        this.timelineRepository = timelineRepository;
        this.transitionService = transitionService;
        this.eventPublisher = eventPublisher;
        this.auditPublisher = auditPublisher;
        this.metrics = metrics;
    }

    @Override
    public BundleResponse openBundle(OpenBundleCommand command) {
        return metrics.openBundleTimer().record(() -> doOpenBundle(command));
    }

    private BundleResponse doOpenBundle(OpenBundleCommand command) {
        CallerContext caller = command.caller();
        Case aCase = loadCaseForCaller(command.caseId(), caller);

        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        // The factory refuses a terminal case and attaches the bundle id to the case aggregate.
        Bundle bundle = BundleFactory.create(aCase, command.bundleType(),
                command.expectedDocumentCount(), caller.asActor(), correlationId, traceId);

        BundleWorkflowFactory.NewBundleWorkflow created = BundleWorkflowFactory.create(
                bundle.bundleId(), aCase.caseId(), caller.tenantId(), caller.asActor());
        BundleWorkflow workflow = created.workflow();
        BundleTimeline timeline = created.timeline();

        caseRepository.save(aCase);          // persists the new bundle id on the case
        bundleRepository.save(bundle);
        workflowRepository.save(workflow);
        timelineRepository.save(timeline);

        eventPublisher.publishAll(bundle.pullDomainEvents());
        eventPublisher.publishAll(workflow.pullDomainEvents());

        auditPublisher.publishBundleCreated(
                bundle.bundleId().value(), aCase.caseId().value(), caller.userId(), caller.role().name(),
                caller.tenantId(), bundle.bundleType().name(), correlationId);
        metrics.recordBundleCreated(bundle.bundleType().name());

        return BundleResponse.from(bundle, workflow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BundleResponse> listBundles(CaseId caseId, CallerContext caller) {
        loadCaseForCaller(caseId, caller);   // existence + tenant guard
        List<BundleResponse> result = new ArrayList<>();
        for (Bundle bundle : bundleRepository.findByCase(caseId)) {
            BundleWorkflow workflow = workflowRepository.findById(bundle.bundleId())
                    .orElseThrow(() -> new InvariantViolationException(
                            "Bundle " + bundle.bundleId().value() + " has no workflow"));
            result.add(BundleResponse.from(bundle, workflow));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public BundleResponse getBundle(BundleId bundleId, CallerContext caller) {
        BundleWorkflow workflow = loadWorkflowForCaller(bundleId, caller);
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new BundleNotFoundException("Bundle not found: " + bundleId.value()));
        return BundleResponse.from(bundle, workflow);
    }

    @Override
    public BundleResponse changeStatus(ChangeBundleStatusCommand command) {
        return transitionService.changeStatus(command);
    }

    @Override
    @Transactional(readOnly = true)
    public BundleTimelineResponse getTimeline(BundleId bundleId, CallerContext caller) {
        loadWorkflowForCaller(bundleId, caller);   // existence + tenant guard
        BundleTimeline timeline = timelineRepository.findByBundle(bundleId)
                .orElseThrow(() -> new BundleNotFoundException(
                        "No timeline for bundle " + bundleId.value()));
        return BundleTimelineResponse.from(timeline);
    }

    // ---- guards --------------------------------------------------------------------------------

    private Case loadCaseForCaller(CaseId caseId, CallerContext caller) {
        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException("Case not found: " + caseId.value()));
        if (!aCase.tenantId().equals(caller.tenantId())) {
            throw new CaseNotFoundException("Case not found: " + caseId.value());
        }
        return aCase;
    }

    private BundleWorkflow loadWorkflowForCaller(BundleId bundleId, CallerContext caller) {
        BundleWorkflow workflow = workflowRepository.findById(bundleId)
                .orElseThrow(() -> new BundleNotFoundException("Bundle not found: " + bundleId.value()));
        if (!workflow.tenantId().equals(caller.tenantId())) {
            auditPublisher.publishAccessDenied(bundleId.value(), caller.userId(), caller.role().name(),
                    caller.tenantId(), "CROSS_TENANT_ACCESS", caller.correlationId());
            throw new BundleNotFoundException("Bundle not found: " + bundleId.value());
        }
        return workflow;
    }
}
