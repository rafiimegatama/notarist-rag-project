package com.notarist.review.domain;

import com.notarist.review.domain.event.FieldReviewed;
import com.notarist.review.domain.event.ReviewCompleted;
import com.notarist.review.domain.event.ReviewRejected;
import com.notarist.review.domain.event.ReviewVerified;
import com.notarist.review.domain.exception.FieldNotFoundException;
import com.notarist.review.domain.exception.IllegalReviewTransitionException;
import com.notarist.review.domain.exception.ReviewAuthorityException;
import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.Reviewer;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.domain.valueobject.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The aggregate is the single gate for the field rules and the status machine. */
class OcrReviewTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final Reviewer staff = Reviewer.of(UUID.randomUUID(), Role.STAFF);
    private final Reviewer notaris = Reviewer.of(UUID.randomUUID(), Role.NOTARIS);

    private FieldId high;
    private FieldId medium;
    private FieldId low;

    private OcrReview newReview() {
        high = FieldId.generate();
        medium = FieldId.generate();
        low = FieldId.generate();
        List<FieldReview> fields = new ArrayList<>();
        fields.add(FieldReview.extracted(high, "NIK", "NIK", "3174091205880003", 0.98,
                BoundingBox.of(1, 0.3, 0.2, 0.4, 0.05), 0));
        fields.add(FieldReview.extracted(medium, "Alamat", "Alamat", "JL MELATI 12", 0.72,
                BoundingBox.of(1, 0.3, 0.4, 0.5, 0.08), 1));
        fields.add(FieldReview.extracted(low, "Pekerjaan", "Pekerjaan", "KARYAWAN", 0.55,
                BoundingBox.of(1, 0.3, 0.55, 0.45, 0.05), 2));
        return OcrReview.start(ReviewId.generate(), documentId, tenantId, "KTP.pdf", 1,
                false, true, 0.75, fields, List.of());
    }

    @Test
    @DisplayName("a fresh review is PENDING with every field NEEDS_REVIEW")
    void freshReviewPending() {
        OcrReview r = newReview();
        assertThat(r.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(r.remainingCount()).isEqualTo(3);
        assertThat(r.fields()).allMatch(f -> f.decision() == FieldDecision.NEEDS_REVIEW);
    }

    @Test
    @DisplayName("the first field decision opens the review (PENDING → IN_PROGRESS) and raises FieldReviewed")
    void firstDecisionOpensReview() {
        OcrReview r = newReview();
        r.reviewField(high, FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null);

        assertThat(r.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(r.domainEvents()).anyMatch(FieldReviewed.class::isInstance);
        assertThat(r.pullAuditEntries()).hasSize(1);
    }

    @Test
    @DisplayName("only HIGH confidence may be auto-accepted; LOW/MEDIUM require a human decision")
    void autoAcceptOnlyHigh() {
        OcrReview r = newReview();
        r.reviewField(high, FieldDecision.AUTO_ACCEPTED, null, null, staff, null, null);   // HIGH — ok

        assertThatThrownBy(() ->
                r.reviewField(low, FieldDecision.AUTO_ACCEPTED, null, null, staff, null, null))
                .isInstanceOf(ReviewInvariantViolationException.class);
        assertThatThrownBy(() ->
                r.reviewField(medium, FieldDecision.AUTO_ACCEPTED, null, null, staff, null, null))
                .isInstanceOf(ReviewInvariantViolationException.class);
    }

    @Test
    @DisplayName("rejecting a field requires a reason and raises ReviewRejected")
    void rejectRequiresReason() {
        OcrReview r = newReview();
        assertThatThrownBy(() ->
                r.reviewField(low, FieldDecision.REJECTED, null, "   ", staff, null, null))
                .isInstanceOf(ReviewInvariantViolationException.class);

        r.reviewField(low, FieldDecision.REJECTED, null, "unreadable", staff, null, null);
        assertThat(r.domainEvents()).anyMatch(ReviewRejected.class::isInstance);
        assertThat(r.rejectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a correction preserves the original OCR value and records the corrected one")
    void correctionPreservesOriginal() {
        OcrReview r = newReview();
        r.reviewField(medium, FieldDecision.CORRECTED, "JL MELATI NO 12", null, staff, null, null);

        FieldReview f = r.fields().stream().filter(x -> x.fieldId().equals(medium)).findFirst().orElseThrow();
        assertThat(f.extractedValue()).isEqualTo("JL MELATI 12");     // never overwritten
        assertThat(f.correctedValue()).isEqualTo("JL MELATI NO 12");
        assertThat(f.effectiveValue()).isEqualTo("JL MELATI NO 12");
        assertThat(r.correctedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown field id is rejected")
    void unknownField() {
        OcrReview r = newReview();
        assertThatThrownBy(() ->
                r.reviewField(FieldId.generate(), FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null))
                .isInstanceOf(FieldNotFoundException.class);
    }

    @Test
    @DisplayName("a SYSTEM actor may not review")
    void systemMayNotReview() {
        OcrReview r = newReview();
        Reviewer system = Reviewer.of(UUID.randomUUID(), Role.SYSTEM);
        assertThatThrownBy(() ->
                r.reviewField(high, FieldDecision.MANUAL_ACCEPTED, null, null, system, null, null))
                .isInstanceOf(ReviewAuthorityException.class);
    }

    @Test
    @DisplayName("completing a review with undecided fields is refused")
    void cannotCompleteWithUndecidedFields() {
        OcrReview r = newReview();
        r.reviewField(high, FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null);   // → IN_PROGRESS
        assertThatThrownBy(() ->
                r.changeStatus(ReviewStatus.REVIEW_COMPLETED, staff, null, null))
                .isInstanceOf(ReviewInvariantViolationException.class);
    }

    @Test
    @DisplayName("an illegal status jump is impossible")
    void illegalStatusJump() {
        OcrReview r = newReview();
        assertThatThrownBy(() ->
                r.changeStatus(ReviewStatus.VERIFIED, notaris, null, null))
                .isInstanceOf(IllegalReviewTransitionException.class);
    }

    @Test
    @DisplayName("STAFF may complete a review but only elevated roles may VERIFY it")
    void verifyRequiresElevatedRole() {
        OcrReview r = decideAll(newReview());
        r.changeStatus(ReviewStatus.REVIEW_COMPLETED, staff, null, null);
        assertThat(r.domainEvents()).anyMatch(ReviewCompleted.class::isInstance);

        assertThatThrownBy(() ->
                r.changeStatus(ReviewStatus.VERIFIED, staff, null, null))
                .isInstanceOf(ReviewAuthorityException.class);

        r.changeStatus(ReviewStatus.VERIFIED, notaris, null, null);
        assertThat(r.status()).isEqualTo(ReviewStatus.VERIFIED);
        assertThat(r.domainEvents()).anyMatch(ReviewVerified.class::isInstance);
    }

    @Test
    @DisplayName("a VERIFIED review is terminal — its fields can no longer change")
    void verifiedReviewIsTerminal() {
        OcrReview r = decideAll(newReview());
        r.changeStatus(ReviewStatus.REVIEW_COMPLETED, staff, null, null);
        r.changeStatus(ReviewStatus.VERIFIED, notaris, null, null);

        assertThatThrownBy(() ->
                r.reviewField(high, FieldDecision.MANUAL_ACCEPTED, null, null, notaris, null, null))
                .isInstanceOf(IllegalReviewTransitionException.class);
    }

    @Test
    @DisplayName("every field decision appends one audit entry, in dense sequence")
    void auditIsAppendOnlyAndDense() {
        OcrReview r = newReview();
        r.reviewField(high, FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null);
        r.reviewField(medium, FieldDecision.CORRECTED, "X", null, staff, null, null);
        r.reviewField(low, FieldDecision.REJECTED, null, "bad", staff, null, null);

        assertThat(r.lastAuditSequence()).isEqualTo(3);
        assertThat(r.pullAuditEntries())
                .extracting(a -> a.sequence())
                .containsExactly(1, 2, 3);
    }

    private OcrReview decideAll(OcrReview r) {
        r.reviewField(high, FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null);
        r.reviewField(medium, FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null);
        r.reviewField(low, FieldDecision.MANUAL_ACCEPTED, null, null, staff, null, null);
        return r;
    }
}
