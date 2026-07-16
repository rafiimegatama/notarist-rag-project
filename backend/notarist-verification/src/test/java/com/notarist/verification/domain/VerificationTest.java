package com.notarist.verification.domain;

import com.notarist.verification.domain.event.ChecklistCompleted;
import com.notarist.verification.domain.event.VerificationCompleted;
import com.notarist.verification.domain.event.VerificationFailed;
import com.notarist.verification.domain.event.VerificationReturned;
import com.notarist.verification.domain.exception.ChecklistItemNotFoundException;
import com.notarist.verification.domain.exception.IllegalVerificationTransitionException;
import com.notarist.verification.domain.exception.VerificationAuthorityException;
import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.Reviewer;
import com.notarist.verification.domain.valueobject.Role;
import com.notarist.verification.domain.valueobject.VerificationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The aggregate is the single gate for the completion rule and the status machine. */
class VerificationTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final Reviewer staff = Reviewer.of(UUID.randomUUID(), Role.STAFF);
    private final Reviewer notaris = Reviewer.of(UUID.randomUUID(), Role.NOTARIS);

    private ItemId mandatoryA;
    private ItemId mandatoryB;
    private ItemId optional;

    private Verification newVerification() {
        mandatoryA = ItemId.generate();
        mandatoryB = ItemId.generate();
        optional = ItemId.generate();
        List<ChecklistItem> items = new ArrayList<>();
        items.add(ChecklistItem.create(mandatoryA, ChecklistCategory.AUTHORITY, "Authority", true, CheckType.AUTOMATIC, 0));
        items.add(ChecklistItem.create(mandatoryB, ChecklistCategory.IDENTITY, "Identity", true, CheckType.MANUAL, 1));
        items.add(ChecklistItem.create(optional, ChecklistCategory.SUPPORTING_DOCUMENTS, "Condition", false, CheckType.MANUAL, 2));
        return Verification.start(VerificationId.generate(), bundleId, tenantId, items);
    }

    @Test
    @DisplayName("a fresh verification is PENDING with every item undecided")
    void freshPending() {
        Verification v = newVerification();
        assertThat(v.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(v.remainingCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("the first decision opens the verification (PENDING → UNDER_VERIFICATION)")
    void firstDecisionOpens() {
        Verification v = newVerification();
        v.decideItem(mandatoryA, Decision.PASS, null, staff, null, null);
        assertThat(v.status()).isEqualTo(VerificationStatus.UNDER_VERIFICATION);
        assertThat(v.pullAuditEntries()).hasSize(1);
    }

    @Test
    @DisplayName("failing an item requires a reason")
    void failRequiresReason() {
        Verification v = newVerification();
        assertThatThrownBy(() -> v.decideItem(mandatoryA, Decision.FAIL, "  ", staff, null, null))
                .isInstanceOf(VerificationInvariantViolationException.class);
        v.decideItem(mandatoryA, Decision.FAIL, "authority mismatch", staff, null, null);
        assertThat(v.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deciding the last item raises ChecklistCompleted exactly once")
    void checklistCompletedRaised() {
        Verification v = newVerification();
        v.decideItem(mandatoryA, Decision.PASS, null, staff, null, null);
        v.decideItem(mandatoryB, Decision.PASS, null, staff, null, null);
        assertThat(v.domainEvents()).noneMatch(ChecklistCompleted.class::isInstance);
        v.decideItem(optional, Decision.NOT_APPLICABLE, null, staff, null, null);
        assertThat(v.domainEvents()).filteredOn(ChecklistCompleted.class::isInstance).hasSize(1);
        assertThat(v.checklistComplete()).isTrue();
    }

    @Test
    @DisplayName("an unknown item id is rejected")
    void unknownItem() {
        Verification v = newVerification();
        assertThatThrownBy(() -> v.decideItem(ItemId.generate(), Decision.PASS, null, staff, null, null))
                .isInstanceOf(ChecklistItemNotFoundException.class);
    }

    @Test
    @DisplayName("a SYSTEM actor may not verify")
    void systemMayNotVerify() {
        Verification v = newVerification();
        Reviewer system = Reviewer.of(UUID.randomUUID(), Role.SYSTEM);
        assertThatThrownBy(() -> v.decideItem(mandatoryA, Decision.PASS, null, system, null, null))
                .isInstanceOf(VerificationAuthorityException.class);
    }

    @Test
    @DisplayName("cannot VERIFY while a mandatory item is FAIL or undecided")
    void cannotVerifyWithUnmetMandatory() {
        Verification v = newVerification();
        v.decideItem(mandatoryA, Decision.FAIL, "bad", staff, null, null);   // opens + a mandatory FAIL
        assertThatThrownBy(() -> v.changeStatus(VerificationStatus.VERIFIED, notaris, null, null))
                .isInstanceOf(VerificationInvariantViolationException.class);
    }

    @Test
    @DisplayName("a MANUAL_REQUIRED item blocks completion")
    void manualRequiredBlocks() {
        Verification v = passAllMandatory();
        v.decideItem(optional, Decision.MANUAL_REQUIRED, null, staff, null, null);   // non-mandatory but blocking
        assertThatThrownBy(() -> v.changeStatus(VerificationStatus.VERIFIED, notaris, null, null))
                .isInstanceOf(VerificationInvariantViolationException.class);
    }

    @Test
    @DisplayName("only elevated roles may VERIFY; STAFF may not")
    void verifyRequiresElevatedRole() {
        Verification v = passAllMandatory();
        assertThatThrownBy(() -> v.changeStatus(VerificationStatus.VERIFIED, staff, null, null))
                .isInstanceOf(VerificationAuthorityException.class);
        v.changeStatus(VerificationStatus.VERIFIED, notaris, null, null);
        assertThat(v.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(v.domainEvents()).anyMatch(VerificationCompleted.class::isInstance);
    }

    @Test
    @DisplayName("an illegal status jump is impossible")
    void illegalJump() {
        Verification v = newVerification();
        assertThatThrownBy(() -> v.changeStatus(VerificationStatus.VERIFIED, notaris, null, null))
                .isInstanceOf(IllegalVerificationTransitionException.class);
    }

    @Test
    @DisplayName("FAILED, then returned to UNDER_VERIFICATION, raises VerificationFailed then VerificationReturned")
    void failThenReturn() {
        Verification v = newVerification();
        v.decideItem(mandatoryA, Decision.PASS, null, staff, null, null);   // opens
        v.changeStatus(VerificationStatus.FAILED, staff, null, null);
        assertThat(v.domainEvents()).anyMatch(VerificationFailed.class::isInstance);

        v.changeStatus(VerificationStatus.UNDER_VERIFICATION, staff, null, null);   // return
        assertThat(v.status()).isEqualTo(VerificationStatus.UNDER_VERIFICATION);
        assertThat(v.domainEvents()).anyMatch(VerificationReturned.class::isInstance);
    }

    @Test
    @DisplayName("an outcome verification refuses item edits until returned")
    void outcomeBlocksItemEdits() {
        Verification v = passAllMandatory();
        v.changeStatus(VerificationStatus.VERIFIED, notaris, null, null);
        assertThatThrownBy(() -> v.decideItem(optional, Decision.PASS, null, staff, null, null))
                .isInstanceOf(IllegalVerificationTransitionException.class);
    }

    @Test
    @DisplayName("each decision appends one audit entry in dense sequence")
    void auditDense() {
        Verification v = newVerification();
        v.decideItem(mandatoryA, Decision.PASS, null, staff, null, null);
        v.decideItem(mandatoryB, Decision.NOT_APPLICABLE, null, staff, null, null);
        assertThat(v.lastAuditSequence()).isEqualTo(2);
        assertThat(v.pullAuditEntries()).extracting(a -> a.sequence()).containsExactly(1, 2);
    }

    /** Opens the verification and settles both mandatory items acceptably; the optional stays undecided. */
    private Verification passAllMandatory() {
        Verification v = newVerification();
        v.decideItem(mandatoryA, Decision.PASS, null, staff, null, null);
        v.decideItem(mandatoryB, Decision.NOT_APPLICABLE, null, staff, null, null);
        return v;
    }
}
