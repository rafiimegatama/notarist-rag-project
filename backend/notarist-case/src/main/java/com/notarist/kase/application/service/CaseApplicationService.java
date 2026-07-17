package com.notarist.kase.application.service;

import com.notarist.core.api.response.PageResponse;
import com.notarist.core.domain.event.DomainEvent;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.api.response.ActivityResponse;
import com.notarist.kase.api.response.CaseResponse;
import com.notarist.kase.api.response.TimelineResponse;
import com.notarist.kase.application.command.ChangeCaseStatusCommand;
import com.notarist.kase.application.command.OpenCaseCommand;
import com.notarist.kase.application.port.in.CaseManagementUseCase;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.application.query.CaseFilter;
import com.notarist.kase.domain.event.CaseTransitioned;
import com.notarist.kase.domain.exception.CaseNotFoundException;
import com.notarist.kase.domain.exception.DuplicateCaseNumberException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.factory.CaseFactory;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.state.TransitionKind;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseNumber;
import com.notarist.kase.domain.valueobject.TransitionReason;
import com.notarist.kase.infrastructure.event.CaseAuditEventPublisher;
import com.notarist.kase.infrastructure.observability.CaseMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates the Case use cases. Holds NO business rules of its own: it loads aggregates, asks
 * them to do the work ({@code aCase.transition(...)}, {@code timeline.append(...)}), persists the
 * result, then publishes the events the aggregates raised. It never decides whether a transition is
 * legal — the aggregate does — and it never touches a state field directly.
 *
 * <p>Every write is one transaction, so the case and its timeline commit together, the optimistic
 * lock on the case guards concurrent status changes, and events are published only after a
 * successful save.
 */
@Service
@Transactional
public class CaseApplicationService implements CaseManagementUseCase {

    private final CaseRepository caseRepository;
    private final TimelineRepository timelineRepository;
    private final DomainEventPublisher eventPublisher;
    private final CaseAuditEventPublisher auditPublisher;
    private final CaseMetrics metrics;

    public CaseApplicationService(CaseRepository caseRepository,
                                  TimelineRepository timelineRepository,
                                  DomainEventPublisher eventPublisher,
                                  CaseAuditEventPublisher auditPublisher,
                                  CaseMetrics metrics) {
        this.caseRepository = caseRepository;
        this.timelineRepository = timelineRepository;
        this.eventPublisher = eventPublisher;
        this.auditPublisher = auditPublisher;
        this.metrics = metrics;
    }

    @Override
    public CaseResponse openCase(OpenCaseCommand command) {
        return metrics.openCaseTimer().record(() -> doOpenCase(command));
    }

    private CaseResponse doOpenCase(OpenCaseCommand command) {
        CallerContext caller = command.caller();
        Actor actor = caller.asActor();
        CaseNumber caseNumber = CaseNumber.of(command.caseNumber());   // bad format → IllegalArgumentException → 400
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        // Friendly 409 ahead of the unique (tenant, case_number) constraint. RLS scopes the lookup to
        // the caller's tenant, so this cannot collide with another tenant's number.
        if (caseRepository.findByCaseNumber(caller.tenantId(), caseNumber).isPresent()) {
            throw new DuplicateCaseNumberException(
                    "Case number " + command.caseNumber() + " already exists for this tenant");
        }

        CaseFactory.NewCase created = CaseFactory.create(
                caseNumber, command.caseType(), caller.tenantId(), actor,
                command.assignedNotarisId(), correlationId, traceId);

        Case aCase = created.aCase();
        Timeline timeline = created.timeline();

        caseRepository.save(aCase);
        timelineRepository.save(timeline);

        publishEvents(aCase.pullDomainEvents());
        publishEvents(timeline.pullDomainEvents());

        auditPublisher.publishCaseCreated(
                aCase.caseId().value(), caller.userId(), caller.role().name(),
                caller.tenantId(), aCase.caseNumber().value(), correlationId);
        metrics.recordCaseCreated(aCase.caseType().name());

        return CaseResponse.from(aCase);
    }

    @Override
    public CaseResponse changeStatus(ChangeCaseStatusCommand command) {
        return metrics.changeStatusTimer().record(() -> doChangeStatus(command));
    }

    private CaseResponse doChangeStatus(ChangeCaseStatusCommand command) {
        CallerContext caller = command.caller();
        Case aCase = loadForCaller(command.caseId(), caller);

        CaseState from = aCase.state();
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();
        TransitionReason reason = command.reason() != null
                ? TransitionReason.of(command.reason())
                : TransitionReason.NONE;

        // The aggregate is the ONLY thing that decides whether this move is legal, who may take it,
        // and whether a reason is required. Any of those failing throws a domain exception here.
        aCase.transition(command.targetState(), caller.asActor(), reason, correlationId, traceId);
        CaseState to = aCase.state();

        // Every transition writes to the case's own append-only story.
        Timeline timeline = timelineRepository.findByCase(aCase.caseId())
                .orElseThrow(() -> new InvariantViolationException(
                        "Case " + aCase.caseId() + " has no timeline — a case is always created with one"));

        TransitionKind kind = transitionKindOf(aCase);
        timeline.append(timelineTypeFor(kind), describe(from, to, kind, reason),
                caller.asActor(), correlationId, traceId);

        // A case that has reached a terminal state seals its story: no further chapter can be added.
        if (aCase.isTerminal() && timeline.status().acceptsEntries()) {
            timeline.seal(correlationId, traceId);
        }

        caseRepository.save(aCase);
        timelineRepository.save(timeline);

        publishEvents(aCase.pullDomainEvents());
        publishEvents(timeline.pullDomainEvents());

        auditPublisher.publishStatusChanged(
                aCase.caseId().value(), caller.userId(), caller.role().name(),
                caller.tenantId(), from.name(), to.name(), correlationId);
        metrics.recordTransition(from.name(), to.name(), kind.name());

        return CaseResponse.from(aCase);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getCase(CaseId caseId, CallerContext caller) {
        return CaseResponse.from(loadForCaller(caseId, caller));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> listCases(CaseFilter filter, int page, int size, CallerContext caller) {
        List<CaseResponse> items = caseRepository.search(caller.tenantId(), filter, page, size)
                .stream().map(CaseResponse::from).toList();
        long total = caseRepository.count(caller.tenantId(), filter);
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public TimelineResponse getTimeline(CaseId caseId, CallerContext caller) {
        loadForCaller(caseId, caller);   // tenant/existence guard, and audits a cross-tenant probe
        Timeline timeline = timelineRepository.findByCase(caseId)
                .orElseThrow(() -> new CaseNotFoundException("No timeline for case " + caseId.value()));
        return TimelineResponse.from(timeline);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> getActivities(CaseId caseId, CallerContext caller) {
        loadForCaller(caseId, caller);   // tenant/existence guard, audits a cross-tenant probe
        Timeline timeline = timelineRepository.findByCase(caseId)
                .orElseThrow(() -> new CaseNotFoundException("No timeline for case " + caseId.value()));
        // Newest first — an activity feed reads top-down. The timeline itself stays oldest-first.
        return timeline.entries().stream()
                .sorted(java.util.Comparator.comparingInt(
                        com.notarist.kase.domain.model.TimelineEntry::sequence).reversed())
                .map(ActivityResponse::from)
                .toList();
    }

    // ---- Helpers -------------------------------------------------------------------------------

    /**
     * Loads a case and asserts it belongs to the caller's tenant. Under RLS a cross-tenant id already
     * returns empty; the explicit check is defence-in-depth (and the only guard if RLS is ever a
     * no-op, e.g. a superuser connection). A cross-tenant hit is audited as a denial and then reported
     * as NOT_FOUND, so the caller cannot learn the case exists elsewhere.
     */
    private Case loadForCaller(CaseId caseId, CallerContext caller) {
        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException("Case not found: " + caseId.value()));
        if (!aCase.tenantId().equals(caller.tenantId())) {
            auditPublisher.publishAccessDenied(
                    caseId.value(), caller.userId(), caller.role().name(),
                    caller.tenantId(), "CROSS_TENANT_ACCESS", caller.correlationId());
            throw new CaseNotFoundException("Case not found: " + caseId.value());
        }
        return aCase;
    }

    private void publishEvents(List<DomainEvent> events) {
        eventPublisher.publishAll(events);
    }

    private TransitionKind transitionKindOf(Case aCase) {
        return aCase.domainEvents().stream()
                .filter(CaseTransitioned.class::isInstance)
                .map(CaseTransitioned.class::cast)
                .reduce((first, second) -> second)   // the most recent one
                .map(CaseTransitioned::kind)
                .orElse(TransitionKind.FORWARD);
    }

    private TimelineEntryType timelineTypeFor(TransitionKind kind) {
        return kind == TransitionKind.ROLLBACK ? TimelineEntryType.ROLLBACK : TimelineEntryType.STATE_CHANGED;
    }

    private String describe(CaseState from, CaseState to, TransitionKind kind, TransitionReason reason) {
        String base = "Status " + from + " → " + to + " (" + kind + ")";
        return reason.isPresent() ? base + " — " + reason.value() : base;
    }
}
