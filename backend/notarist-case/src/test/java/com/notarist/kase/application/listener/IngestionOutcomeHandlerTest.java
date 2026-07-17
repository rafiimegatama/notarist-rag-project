package com.notarist.kase.application.listener;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.kase.application.port.in.DocumentIngestionOutcome;
import com.notarist.kase.application.port.out.CaseRepository;
import com.notarist.kase.application.port.out.DomainEventPublisher;
import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.CaseId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionOutcomeHandlerTest {

    private CaseRepository caseRepository;
    private TimelineRepository timelineRepository;
    private DomainEventPublisher eventPublisher;
    private com.notarist.kase.infrastructure.observability.CaseMetrics metrics;
    private IngestionOutcomeHandler handler;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        timelineRepository = mock(TimelineRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        metrics = mock(com.notarist.kase.infrastructure.observability.CaseMetrics.class);
        handler = new IngestionOutcomeHandler(caseRepository, timelineRepository, eventPublisher, metrics);
    }

    private DocumentIngestionOutcome outcome(CaseId caseId, boolean succeeded) {
        return new DocumentIngestionOutcome(new DocumentId(UUID.randomUUID()), caseId, null, succeeded);
    }

    @Test
    @DisplayName("success advances the case, appends the timeline, records the metric, publishes events")
    void successAdvancesCaseAndWritesTimelineAndMetric() {
        CaseId caseId = CaseId.generate();
        Case aCase = mock(Case.class);
        when(aCase.caseId()).thenReturn(caseId);
        // guard read, 'from' read, 'to' read after the (mocked, no-op) transition
        when(aCase.state()).thenReturn(
                CaseState.OCR_RUNNING, CaseState.OCR_RUNNING, CaseState.FIELD_EXTRACTION);
        when(aCase.pullDomainEvents()).thenReturn(List.of());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(aCase));

        Timeline timeline = mock(Timeline.class);
        when(timeline.pullDomainEvents()).thenReturn(List.of());
        when(timelineRepository.findByCase(caseId)).thenReturn(Optional.of(timeline));

        handler.handle(outcome(caseId, true));

        verify(aCase).transition(eq(CaseState.FIELD_EXTRACTION), any(Actor.class));
        verify(timeline).append(eq(TimelineEntryType.STATE_CHANGED), anyString(), any(Actor.class),
                any(), any());
        verify(caseRepository).save(aCase);
        verify(timelineRepository).save(timeline);
        verify(metrics).recordTransition("OCR_RUNNING", "FIELD_EXTRACTION", "FORWARD");
        verify(eventPublisher, times(2)).publishAll(any());   // case events + timeline events
    }

    @Test
    @DisplayName("failure advances the case to OCR_FAILED")
    void failureAdvancesToOcrFailed() {
        CaseId caseId = CaseId.generate();
        Case aCase = mock(Case.class);
        when(aCase.caseId()).thenReturn(caseId);
        when(aCase.state()).thenReturn(
                CaseState.OCR_RUNNING, CaseState.OCR_RUNNING, CaseState.OCR_FAILED);
        when(aCase.pullDomainEvents()).thenReturn(List.of());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(aCase));
        Timeline timeline = mock(Timeline.class);
        when(timeline.pullDomainEvents()).thenReturn(List.of());
        when(timelineRepository.findByCase(caseId)).thenReturn(Optional.of(timeline));

        handler.handle(outcome(caseId, false));

        verify(aCase).transition(eq(CaseState.OCR_FAILED), any(Actor.class));
        verify(metrics).recordTransition("OCR_RUNNING", "OCR_FAILED", "FORWARD");
    }

    @Test
    @DisplayName("a case not awaiting OCR is left untouched — no transition, no timeline write")
    void ignoresCaseNotAwaitingOcr() {
        CaseId caseId = CaseId.generate();
        Case aCase = mock(Case.class);
        when(aCase.caseId()).thenReturn(caseId);
        when(aCase.state()).thenReturn(CaseState.FIELD_EXTRACTION);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(aCase));

        handler.handle(outcome(caseId, true));

        verify(aCase, never()).transition(any(), any());
        verify(timelineRepository, never()).save(any());
        verify(metrics, never()).recordTransition(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an unknown case is ignored")
    void ignoresUnknownCase() {
        CaseId caseId = CaseId.generate();
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        handler.handle(outcome(caseId, true));

        verify(caseRepository, never()).save(any());
        verify(timelineRepository, never()).save(any());
    }
}
