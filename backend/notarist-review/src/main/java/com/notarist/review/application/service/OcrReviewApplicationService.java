package com.notarist.review.application.service;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.review.api.response.OcrReviewResponse;
import com.notarist.review.api.response.OcrReviewSummaryResponse;
import com.notarist.review.application.command.ChangeReviewStatusCommand;
import com.notarist.review.application.command.InitializeReviewCommand;
import com.notarist.review.application.command.ReviewFieldCommand;
import com.notarist.review.application.port.in.OcrReviewProvisioningUseCase;
import com.notarist.review.application.port.in.OcrReviewUseCase;
import com.notarist.review.application.port.out.DomainEventPublisher;
import com.notarist.review.application.port.out.OcrReviewRepository;
import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import com.notarist.review.domain.exception.ReviewNotFoundException;
import com.notarist.review.domain.model.AuthorityItem;
import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.AuthorityId;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.infrastructure.event.ReviewAuditEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the OCR review use cases. Holds NO business rules of its own: it loads the aggregate,
 * asks it to do the work ({@code review.reviewField(...)}, {@code review.changeStatus(...)}), persists
 * the result, then publishes the events the aggregate raised. Legality of transitions, field rules and
 * authority all live inside the aggregate — never here.
 *
 * <p>Every write is one transaction, so the review, its field decisions and the append-only audit
 * rows commit together, the optimistic lock on the review guards concurrent reviewers, and events are
 * published only after a successful save.
 */
@Service
@Transactional
public class OcrReviewApplicationService implements OcrReviewUseCase, OcrReviewProvisioningUseCase {

    private final OcrReviewRepository repository;
    private final DomainEventPublisher eventPublisher;
    private final ReviewAuditEventPublisher auditPublisher;

    public OcrReviewApplicationService(OcrReviewRepository repository,
                                       DomainEventPublisher eventPublisher,
                                       ReviewAuditEventPublisher auditPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.auditPublisher = auditPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public OcrReviewResponse getReview(DocumentId documentId, CallerContext caller) {
        return OcrReviewResponse.from(loadForCaller(documentId, caller));
    }

    @Override
    @Transactional(readOnly = true)
    public OcrReviewSummaryResponse getSummary(DocumentId documentId, CallerContext caller) {
        return OcrReviewSummaryResponse.from(loadForCaller(documentId, caller));
    }

    @Override
    public OcrReviewResponse reviewField(ReviewFieldCommand command) {
        CallerContext caller = command.caller();
        OcrReview review = loadForCaller(command.documentId(), caller);
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        // The aggregate is the ONLY thing that applies the field rules (auto-accept only on HIGH,
        // reject requires a reason, …) and writes the append-only audit entry.
        review.reviewField(command.fieldId(), command.decision(), command.correctedValue(),
                command.reason(), caller.asReviewer(), correlationId, traceId);

        repository.save(review);
        publishEvents(review.pullDomainEvents());

        auditPublisher.publishFieldReviewed(
                review.reviewId().value(), review.documentId(), caller.userId(), caller.role().name(),
                caller.tenantId(), fieldName(review, command.fieldId()), command.decision().name(),
                correlationId);

        return OcrReviewResponse.from(review);
    }

    @Override
    public OcrReviewResponse changeStatus(ChangeReviewStatusCommand command) {
        CallerContext caller = command.caller();
        OcrReview review = loadForCaller(command.documentId(), caller);
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        ReviewStatus from = review.status();
        review.changeStatus(command.targetStatus(), caller.asReviewer(), correlationId, traceId);
        ReviewStatus to = review.status();

        repository.save(review);
        publishEvents(review.pullDomainEvents());

        auditPublisher.publishStatusChanged(
                review.reviewId().value(), review.documentId(), caller.userId(), caller.role().name(),
                caller.tenantId(), from.name(), to.name(), correlationId);

        return OcrReviewResponse.from(review);
    }

    @Override
    public OcrReviewResponse initializeReview(InitializeReviewCommand command) {
        DocumentId documentId = DocumentId.of(command.documentId());
        if (repository.existsByDocumentId(documentId)) {
            throw new ReviewInvariantViolationException(
                    "A review already exists for document " + command.documentId());
        }

        List<FieldReview> fields = new ArrayList<>();
        int order = 0;
        for (InitializeReviewCommand.FieldSpec spec : command.fields()) {
            fields.add(FieldReview.extracted(
                    FieldId.generate(),
                    spec.fieldName(), spec.displayLabel(), spec.extractedValue(), spec.confidence(),
                    BoundingBox.of(spec.page(), spec.x(), spec.y(), spec.width(), spec.height()),
                    order++));
        }

        List<AuthorityItem> authority = new ArrayList<>();
        int authOrder = 0;
        for (InitializeReviewCommand.AuthoritySpec spec : command.authorityItems()) {
            authority.add(AuthorityItem.extracted(
                    AuthorityId.generate(),
                    spec.type(), spec.roleLabel(), spec.personName(), spec.content(), spec.confidence(),
                    authOrder++));
        }

        OcrReview review = OcrReview.start(
                ReviewId.generate(), command.documentId(), command.tenantId(), command.documentName(),
                command.pageCount(), command.stampDetected(), command.signatureDetected(),
                command.overallConfidence(), fields, authority);

        repository.save(review);
        return OcrReviewResponse.from(review);
    }

    // ---- helpers -------------------------------------------------------------------------------

    /**
     * Loads a review by document and asserts it belongs to the caller's tenant. Under RLS a
     * cross-tenant document already returns empty; the explicit check is defence-in-depth. A
     * cross-tenant hit is audited as a denial and then reported as NOT_FOUND, so the caller cannot
     * learn the review exists elsewhere.
     */
    private OcrReview loadForCaller(DocumentId documentId, CallerContext caller) {
        OcrReview review = repository.findByDocumentId(documentId)
                .orElseThrow(() -> new ReviewNotFoundException(
                        "No OCR review for document " + documentId.value()));
        if (!review.tenantId().equals(caller.tenantId())) {
            auditPublisher.publishAccessDenied(
                    documentId.value(), caller.userId(), caller.role().name(),
                    caller.tenantId(), "CROSS_TENANT_ACCESS", caller.correlationId());
            throw new ReviewNotFoundException("No OCR review for document " + documentId.value());
        }
        return review;
    }

    private String fieldName(OcrReview review, FieldId fieldId) {
        return review.fields().stream()
                .filter(f -> f.fieldId().equals(fieldId))
                .map(FieldReview::fieldName)
                .findFirst()
                .orElse(fieldId.toString());
    }

    private void publishEvents(List<com.notarist.core.domain.event.DomainEvent> events) {
        eventPublisher.publishAll(events);
    }
}
