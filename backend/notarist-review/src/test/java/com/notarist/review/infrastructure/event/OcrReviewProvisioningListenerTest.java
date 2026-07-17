package com.notarist.review.infrastructure.event;

import com.notarist.core.api.event.OcrReviewProvisioningRequested;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.review.application.command.InitializeReviewCommand;
import com.notarist.review.application.port.in.OcrReviewProvisioningUseCase;
import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcrReviewProvisioningListenerTest {

    private OcrReviewProvisioningUseCase useCase;
    private OcrReviewProvisioningListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(OcrReviewProvisioningUseCase.class);
        listener = new OcrReviewProvisioningListener(useCase);
        VpdContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    private OcrReviewProvisioningRequested event(UUID documentId, UUID tenantId, UUID uploadedBy) {
        return new OcrReviewProvisioningRequested(documentId, tenantId, uploadedBy, "akta.pdf", 3, 0.91);
    }

    @Test
    @DisplayName("maps the OCR event onto initializeReview: identity, name, page count, confidence")
    void mapsEventOntoCommand() {
        UUID documentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID uploadedBy = UUID.randomUUID();

        listener.onOcrReviewProvisioningRequested(event(documentId, tenantId, uploadedBy));

        ArgumentCaptor<InitializeReviewCommand> captor = ArgumentCaptor.forClass(InitializeReviewCommand.class);
        verify(useCase).initializeReview(captor.capture());
        InitializeReviewCommand cmd = captor.getValue();

        assertThat(cmd.documentId()).isEqualTo(documentId);
        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.documentName()).isEqualTo("akta.pdf");
        assertThat(cmd.pageCount()).isEqualTo(3);
        assertThat(cmd.overallConfidence()).isEqualTo(0.91);
        // OCR asserts neither of these — they are the reviewer's job, so provisioning must not claim them.
        assertThat(cmd.stampDetected()).isFalse();
        assertThat(cmd.signatureDetected()).isFalse();
        assertThat(cmd.fields()).isEmpty();
        assertThat(cmd.authorityItems()).isEmpty();
    }

    @Test
    @DisplayName("installs the event's tenant identity for the duration of the call, then clears it")
    void installsAndClearsTenantIdentity() {
        UUID tenantId = UUID.randomUUID();
        UUID uploadedBy = UUID.randomUUID();
        AtomicReference<UUID> tenantSeenDuringCall = new AtomicReference<>();

        when(useCase.initializeReview(any())).thenAnswer(inv -> {
            tenantSeenDuringCall.set(VpdContextHolder.get().map(VpdContextHolder.VpdPrincipal::tenantId).orElse(null));
            return null;
        });

        listener.onOcrReviewProvisioningRequested(event(UUID.randomUUID(), tenantId, uploadedBy));

        // The RLS identity must be the event's own tenant while the write happens — otherwise the
        // fail-closed policy on ocr_review rejects the insert.
        assertThat(tenantSeenDuringCall.get()).isEqualTo(tenantId);
        // And it must not leak to the next task on this (pooled) thread.
        assertThat(VpdContextHolder.get()).isEmpty();
    }

    @Test
    @DisplayName("a pre-existing review is an idempotent no-op, not a failure")
    void idempotentWhenReviewAlreadyExists() {
        when(useCase.initializeReview(any()))
                .thenThrow(new ReviewInvariantViolationException("already exists"));

        assertThatCode(() -> listener.onOcrReviewProvisioningRequested(
                event(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .doesNotThrowAnyException();
        assertThat(VpdContextHolder.get()).isEmpty();
    }
}
