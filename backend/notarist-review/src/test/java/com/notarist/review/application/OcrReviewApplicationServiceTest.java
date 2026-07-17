package com.notarist.review.application;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.review.api.response.OcrReviewResponse;
import com.notarist.review.application.command.ChangeReviewStatusCommand;
import com.notarist.review.application.command.ReviewFieldCommand;
import com.notarist.review.application.port.out.DomainEventPublisher;
import com.notarist.review.application.port.out.OcrReviewRepository;
import com.notarist.review.application.query.CallerContext;
import com.notarist.review.application.service.OcrReviewApplicationService;
import com.notarist.review.domain.exception.ReviewNotFoundException;
import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.domain.valueobject.Role;
import com.notarist.review.infrastructure.event.ReviewAuditEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The service orchestrates; it never decides. Verified against mocked ports. */
class OcrReviewApplicationServiceTest {

    private OcrReviewRepository repository;
    private DomainEventPublisher events;
    private ReviewAuditEventPublisher audit;
    private OcrReviewApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private FieldId fieldId;

    @BeforeEach
    void setUp() {
        repository = mock(OcrReviewRepository.class);
        events = mock(DomainEventPublisher.class);
        audit = mock(ReviewAuditEventPublisher.class);
        service = new OcrReviewApplicationService(repository, events, audit);
    }

    private CallerContext caller(UUID tenant, Role role) {
        return new CallerContext(userId, tenant, role, CorrelationId.generate());
    }

    private OcrReview review(UUID tenant) {
        fieldId = FieldId.generate();
        FieldReview f = FieldReview.extracted(fieldId, "NIK", "NIK", "123", 0.98,
                BoundingBox.of(1, 0.1, 0.1, 0.2, 0.05), 0);
        return OcrReview.start(ReviewId.generate(), documentId, tenant, "KTP.pdf", 1,
                false, true, 0.9, List.of(f), List.of());
    }

    @Test
    @DisplayName("getReview returns the mapped payload for the owning tenant")
    void getReviewHappy() {
        when(repository.findByDocumentId(any(DocumentId.class))).thenReturn(Optional.of(review(tenantId)));

        OcrReviewResponse response = service.getReview(DocumentId.of(documentId), caller(tenantId, Role.STAFF));

        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(response.fields()).hasSize(1);
        assertThat(response.reviewStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a missing review is NOT_FOUND")
    void getReviewMissing() {
        when(repository.findByDocumentId(any(DocumentId.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReview(DocumentId.of(documentId), caller(tenantId, Role.STAFF)))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    @DisplayName("a cross-tenant review is reported NOT_FOUND and audited as a denial")
    void getReviewCrossTenant() {
        UUID otherTenant = UUID.randomUUID();
        when(repository.findByDocumentId(any(DocumentId.class))).thenReturn(Optional.of(review(otherTenant)));

        assertThatThrownBy(() -> service.getReview(DocumentId.of(documentId), caller(tenantId, Role.STAFF)))
                .isInstanceOf(ReviewNotFoundException.class);
        verify(audit).publishAccessDenied(eq(documentId), eq(userId), anyString(), eq(tenantId),
                anyString(), any());
    }

    @Test
    @DisplayName("reviewField saves, publishes events and audits the decision")
    void reviewFieldHappy() {
        when(repository.findByDocumentId(any(DocumentId.class))).thenReturn(Optional.of(review(tenantId)));

        OcrReviewResponse response = service.reviewField(new ReviewFieldCommand(
                DocumentId.of(documentId), fieldId, FieldDecision.MANUAL_ACCEPTED, null, null,
                caller(tenantId, Role.STAFF)));

        assertThat(response.reviewStatus()).isEqualTo("IN_PROGRESS");
        verify(repository, times(1)).save(any(OcrReview.class));
        verify(events, times(1)).publishAll(any());
        verify(audit, times(1)).publishFieldReviewed(any(), eq(documentId), eq(userId), anyString(),
                eq(tenantId), anyString(), eq("MANUAL_ACCEPTED"), any());
    }

    @Test
    @DisplayName("an illegal/guarded status change is refused by the aggregate and never persisted")
    void changeStatusGuarded() {
        when(repository.findByDocumentId(any(DocumentId.class))).thenReturn(Optional.of(review(tenantId)));

        // PENDING → REVIEW_COMPLETED is not a legal edge; the aggregate refuses it, so nothing is saved.
        assertThatThrownBy(() -> service.changeStatus(new ChangeReviewStatusCommand(
                DocumentId.of(documentId), ReviewStatus.REVIEW_COMPLETED, caller(tenantId, Role.STAFF))))
                .isInstanceOf(RuntimeException.class);
        verify(repository, never()).save(any());
    }
}
