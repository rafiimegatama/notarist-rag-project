package com.notarist.kase.application;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.kase.api.response.CaseResponse;
import com.notarist.kase.api.response.TimelineResponse;
import com.notarist.kase.application.command.ChangeCaseStatusCommand;
import com.notarist.kase.application.command.OpenCaseCommand;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.application.service.CaseApplicationService;
import com.notarist.kase.domain.event.CaseTransitioned;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.CaseNotFoundException;
import com.notarist.kase.domain.exception.DuplicateCaseNumberException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.factory.CaseFactory;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseNumber;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.domain.valueobject.Role;
import com.notarist.kase.infrastructure.event.CaseAuditEventPublisher;
import com.notarist.kase.infrastructure.observability.CaseMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the orchestration in {@link CaseApplicationService}. Repositories and publishers are
 * mocked; the real aggregates and a real (in-memory) metrics registry are used, so the test asserts
 * that the service drives the DOMAIN correctly — it never re-implements a rule the aggregate owns.
 */
class CaseApplicationServiceTest {

    private CaseRepository caseRepository;
    private TimelineRepository timelineRepository;
    private DomainEventPublisher eventPublisher;
    private CaseAuditEventPublisher auditPublisher;
    private CaseApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        timelineRepository = mock(TimelineRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        auditPublisher = mock(CaseAuditEventPublisher.class);
        CaseMetrics metrics = new CaseMetrics(new SimpleMeterRegistry());
        service = new CaseApplicationService(
                caseRepository, timelineRepository, eventPublisher, auditPublisher, metrics);
    }

    private CallerContext staff() {
        return new CallerContext(staffId, tenantId, Role.STAFF, CorrelationId.generate());
    }

    @Test
    @DisplayName("openCase persists case + timeline, publishes events, returns CASE_CREATED")
    void openCaseHappyPath() {
        when(caseRepository.findByCaseNumber(any(), any())).thenReturn(Optional.empty());

        CaseResponse response = service.openCase(new OpenCaseCommand(
                "12/V/2026", CaseType.FIDUSIA, null, staff()));

        assertThat(response.caseNumber()).isEqualTo("12/V/2026");
        assertThat(response.state()).isEqualTo(CaseState.CASE_CREATED.name());
        assertThat(response.caseType()).isEqualTo("FIDUSIA");
        assertThat(response.terminal()).isFalse();

        verify(caseRepository).save(any(Case.class));
        verify(timelineRepository).save(any(Timeline.class));
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishAll(anyList());
        verify(auditPublisher).publishCaseCreated(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("openCase rejects a case number that already exists for the tenant")
    void openCaseDuplicate() {
        Case existing = CaseFactory.create(
                CaseNumber.of("12/V/2026"), CaseType.FIDUSIA, tenantId,
                Actor.of(staffId, Role.STAFF), null, CorrelationId.generate(), null).aCase();
        when(caseRepository.findByCaseNumber(any(), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.openCase(
                new OpenCaseCommand("12/V/2026", CaseType.FIDUSIA, null, staff())))
                .isInstanceOf(DuplicateCaseNumberException.class);

        verify(caseRepository, never()).save(any());
    }

    @Test
    @DisplayName("openCase rejects a malformed case number with a 400-mapped IllegalArgumentException")
    void openCaseBadNumber() {
        assertThatThrownBy(() -> service.openCase(
                new OpenCaseCommand("not-a-number", CaseType.FIDUSIA, null, staff())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changeStatus moves the case through the aggregate and appends a timeline entry")
    void changeStatusHappyPath() {
        CaseFactory.NewCase created = seededCase();
        Case aCase = created.aCase();
        Timeline timeline = created.timeline();

        when(caseRepository.findById(aCase.caseId())).thenReturn(Optional.of(aCase));
        when(timelineRepository.findByCase(aCase.caseId())).thenReturn(Optional.of(timeline));

        int entriesBefore = timeline.entryCount();

        CaseResponse response = service.changeStatus(new ChangeCaseStatusCommand(
                aCase.caseId(), CaseState.UPLOADING, null, staff()));

        assertThat(response.state()).isEqualTo(CaseState.UPLOADING.name());
        assertThat(aCase.state()).isEqualTo(CaseState.UPLOADING);         // aggregate actually moved
        assertThat(timeline.entryCount()).isEqualTo(entriesBefore + 1);   // story grew by one
        verify(caseRepository).save(aCase);
        verify(timelineRepository).save(timeline);
        verify(auditPublisher).publishStatusChanged(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("CASE_CREATED"),
                org.mockito.ArgumentMatchers.eq("UPLOADING"), any());
    }

    @Test
    @DisplayName("changeStatus surfaces the aggregate's rejection of an illegal edge — nothing is saved")
    void changeStatusIllegalEdge() {
        CaseFactory.NewCase created = seededCase();
        Case aCase = created.aCase();
        when(caseRepository.findById(aCase.caseId())).thenReturn(Optional.of(aCase));

        // CASE_CREATED → FINALIZED is not an edge in the machine.
        assertThatThrownBy(() -> service.changeStatus(new ChangeCaseStatusCommand(
                aCase.caseId(), CaseState.FINALIZED, null, staff())))
                .isInstanceOf(IllegalTransitionException.class);

        verify(caseRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus requires a reason for a CANCEL edge (aggregate invariant)")
    void changeStatusCancelNeedsReason() {
        CaseFactory.NewCase created = seededCase();
        Case aCase = created.aCase();
        when(caseRepository.findById(aCase.caseId())).thenReturn(Optional.of(aCase));
        when(timelineRepository.findByCase(aCase.caseId())).thenReturn(Optional.of(created.timeline()));

        assertThatThrownBy(() -> service.changeStatus(new ChangeCaseStatusCommand(
                aCase.caseId(), CaseState.CANCELLED, null, staff())))
                .isInstanceOf(InvariantViolationException.class);
    }

    @Test
    @DisplayName("SYSTEM-only edges reject a human actor via the aggregate's authority check")
    void changeStatusAuthorityEnforced() {
        // Drive a case to OCR_RUNNING, whose forward edges are SYSTEM-only.
        CaseFactory.NewCase created = seededCase();
        Case aCase = created.aCase();
        aCase.transition(CaseState.UPLOADING, Actor.of(staffId, Role.STAFF));
        aCase.transition(CaseState.OCR_RUNNING, Actor.of(staffId, Role.STAFF));
        aCase.pullDomainEvents();
        when(caseRepository.findById(aCase.caseId())).thenReturn(Optional.of(aCase));
        when(timelineRepository.findByCase(aCase.caseId())).thenReturn(Optional.of(created.timeline()));

        assertThatThrownBy(() -> service.changeStatus(new ChangeCaseStatusCommand(
                aCase.caseId(), CaseState.FIELD_EXTRACTION, null, staff())))
                .isInstanceOf(AuthorityException.class);
    }

    @Test
    @DisplayName("a cross-tenant read is audited as a denial and reported as NOT_FOUND")
    void getCaseCrossTenantIsHidden() {
        Case otherTenant = CaseFactory.create(
                CaseNumber.of("99/V/2026"), CaseType.ROYA, UUID.randomUUID(),
                Actor.of(UUID.randomUUID(), Role.STAFF), null, CorrelationId.generate(), null).aCase();
        when(caseRepository.findById(otherTenant.caseId())).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> service.getCase(otherTenant.caseId(), staff()))
                .isInstanceOf(CaseNotFoundException.class);
        verify(auditPublisher).publishAccessDenied(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("CROSS_TENANT_ACCESS"), any());
    }

    @Test
    @DisplayName("getCase returns NOT_FOUND when the case does not exist")
    void getCaseNotFound() {
        CaseId missing = CaseId.generate();
        when(caseRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCase(missing, staff()))
                .isInstanceOf(CaseNotFoundException.class);
    }

    @Test
    @DisplayName("getTimeline returns the case's story")
    void getTimelineHappyPath() {
        CaseFactory.NewCase created = seededCase();
        when(caseRepository.findById(created.aCase().caseId())).thenReturn(Optional.of(created.aCase()));
        when(timelineRepository.findByCase(created.aCase().caseId()))
                .thenReturn(Optional.of(created.timeline()));

        TimelineResponse response = service.getTimeline(created.aCase().caseId(), staff());

        assertThat(response.caseId()).isEqualTo(created.aCase().caseId().value());
        assertThat(response.entries()).isNotEmpty();
        assertThat(response.entries().get(0).type()).isEqualTo("CASE_OPENED");
    }

    @Test
    @DisplayName("getActivities projects the timeline into activities, newest first")
    void getActivitiesNewestFirst() {
        CaseFactory.NewCase created = seededCase();
        Case aCase = created.aCase();
        Timeline timeline = created.timeline();
        // Two more entries so ordering is observable.
        timeline.append(com.notarist.kase.domain.model.TimelineEntryType.STATE_CHANGED,
                "Status CASE_CREATED → UPLOADING", Actor.of(staffId, Role.STAFF), null, null);
        timeline.append(com.notarist.kase.domain.model.TimelineEntryType.VERIFICATION,
                "Verified", Actor.of(staffId, Role.STAFF), null, null);

        when(caseRepository.findById(aCase.caseId())).thenReturn(Optional.of(aCase));
        when(timelineRepository.findByCase(aCase.caseId())).thenReturn(Optional.of(timeline));

        var activities = service.getActivities(aCase.caseId(), staff());

        assertThat(activities).hasSize(3);
        assertThat(activities.get(0).sequence()).isEqualTo(2);   // newest first
        assertThat(activities.get(2).sequence()).isEqualTo(0);   // CASE_OPENED last
        assertThat(activities.get(2).type()).isEqualTo("CASE_OPENED");
        assertThat(activities.get(0).category()).isEqualTo("REVIEW");   // VERIFICATION → REVIEW
    }

    /** A case + timeline in the tenant, with birth events already drained (as if persisted). */
    private CaseFactory.NewCase seededCase() {
        CaseFactory.NewCase created = CaseFactory.create(
                CaseNumber.of("12/V/2026"), CaseType.FIDUSIA, tenantId,
                Actor.of(staffId, Role.STAFF), null, CorrelationId.generate(), null);
        created.aCase().pullDomainEvents();
        created.timeline().pullDomainEvents();
        return created;
    }

    // Guards a compile-time contract: CaseTransitioned carries the kind the service reads for the
    // timeline entry type. If the event dropped that accessor, the service would silently mis-type
    // every rollback entry.
    @Test
    void caseTransitionedExposesKind() {
        assertThat(CaseTransitioned.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("kind"));
    }
}
