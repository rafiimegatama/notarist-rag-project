package com.notarist.kase.infrastructure.event;

import com.notarist.core.api.event.DocumentIngestionCompleted;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.application.port.in.DocumentIngestionOutcome;
import com.notarist.kase.application.port.in.HandleIngestionOutcomeUseCase;
import com.notarist.kase.application.port.out.BundleRepository;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionCompletedListenerTest {

    private BundleRepository bundleRepository;
    private HandleIngestionOutcomeUseCase handleUseCase;
    private DocumentIngestionCompletedListener listener;

    @BeforeEach
    void setUp() {
        bundleRepository = mock(BundleRepository.class);
        handleUseCase = mock(HandleIngestionOutcomeUseCase.class);
        listener = new DocumentIngestionCompletedListener(bundleRepository, handleUseCase);
        VpdContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    @Test
    @DisplayName("resolves the owning case+bundle from the document id and routes the outcome")
    void routesOutcomeToResolvedCase() {
        UUID documentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CaseId caseId = CaseId.generate();
        BundleId bundleId = BundleId.generate();

        Bundle bundle = mock(Bundle.class);
        when(bundle.caseId()).thenReturn(caseId);
        when(bundle.bundleId()).thenReturn(bundleId);
        when(bundleRepository.findByDocumentId(new DocumentId(documentId))).thenReturn(Optional.of(bundle));

        listener.onDocumentIngestionCompleted(new DocumentIngestionCompleted(documentId, tenantId, true));

        ArgumentCaptor<DocumentIngestionOutcome> captor =
                ArgumentCaptor.forClass(DocumentIngestionOutcome.class);
        verify(handleUseCase).handle(captor.capture());
        DocumentIngestionOutcome outcome = captor.getValue();
        assertThat(outcome.documentId().value()).isEqualTo(documentId);
        assertThat(outcome.caseId()).isEqualTo(caseId);
        assertThat(outcome.bundleId()).isEqualTo(bundleId);
        assertThat(outcome.succeeded()).isTrue();
        // Context restored afterwards.
        assertThat(VpdContextHolder.get()).isEmpty();
    }

    @Test
    @DisplayName("a document in no bundle is ignored — standalone uploads are not an error")
    void ignoresDocumentWithNoBundle() {
        UUID documentId = UUID.randomUUID();
        when(bundleRepository.findByDocumentId(any())).thenReturn(Optional.empty());

        listener.onDocumentIngestionCompleted(
                new DocumentIngestionCompleted(documentId, UUID.randomUUID(), true));

        verify(handleUseCase, never()).handle(any());
    }

    @Test
    @DisplayName("the event's tenant identity is installed while resolving under RLS")
    void installsTenantIdentityDuringResolution() {
        UUID tenantId = UUID.randomUUID();
        AtomicReference<UUID> tenantSeen = new AtomicReference<>();
        when(bundleRepository.findByDocumentId(any())).thenAnswer(inv -> {
            tenantSeen.set(VpdContextHolder.get()
                    .map(VpdContextHolder.VpdPrincipal::tenantId).orElse(null));
            return Optional.empty();
        });

        listener.onDocumentIngestionCompleted(
                new DocumentIngestionCompleted(UUID.randomUUID(), tenantId, false));

        assertThat(tenantSeen.get()).isEqualTo(tenantId);
    }
}
