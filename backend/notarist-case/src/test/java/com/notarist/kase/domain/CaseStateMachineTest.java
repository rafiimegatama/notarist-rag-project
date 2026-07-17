package com.notarist.kase.domain;

import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.factory.CaseFactory;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.state.CaseStateMachine;
import com.notarist.kase.domain.state.TransitionKind;
import com.notarist.kase.domain.valueobject.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Case state machine. These tests exist to prove that an illegal transition is <em>impossible</em>,
 * not merely discouraged — there is no public setter, so the only way to move a case is through a
 * table that rejects everything it does not list.
 */
class CaseStateMachineTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Actor STAFF = Actor.of(UUID.randomUUID(), Role.STAFF);
    private static final Actor NOTARIS = Actor.of(UUID.randomUUID(), Role.NOTARIS);
    private static final Actor ADMIN = Actor.of(UUID.randomUUID(), Role.ADMIN);
    private static final Actor PIMPINAN = Actor.of(UUID.randomUUID(), Role.PIMPINAN);

    private Case newCase() {
        return CaseFactory.create(
                CaseNumber.of("1/VII/2026"), CaseType.FIDUSIA, TENANT, STAFF,
                NOTARIS.userId(), null, null).aCase();
    }

    /** Walks a case to an arbitrary state using only legal transitions. */
    private Case caseAt(CaseState target) {
        Case c = newCase();
        if (target == CaseState.CASE_CREATED) return c;

        c.transition(CaseState.UPLOADING, STAFF);
        if (target == CaseState.UPLOADING) return c;

        c.transition(CaseState.OCR_RUNNING, STAFF);
        if (target == CaseState.OCR_RUNNING) return c;

        if (target == CaseState.OCR_FAILED) {
            c.transition(CaseState.OCR_FAILED, Actor.system());
            return c;
        }

        c.transition(CaseState.FIELD_EXTRACTION, Actor.system());
        if (target == CaseState.FIELD_EXTRACTION) return c;

        c.transition(CaseState.WAITING_VERIFICATION, Actor.system());
        if (target == CaseState.WAITING_VERIFICATION) return c;

        c.transition(CaseState.VERIFIED, STAFF);
        if (target == CaseState.VERIFIED) return c;

        c.transition(CaseState.GENERATING_DRAFT, STAFF);
        if (target == CaseState.GENERATING_DRAFT) return c;

        if (target == CaseState.DRAFT_FAILED) {
            c.transition(CaseState.DRAFT_FAILED, Actor.system());
            return c;
        }

        c.transition(CaseState.WAITING_QC, Actor.system());
        if (target == CaseState.WAITING_QC) return c;

        if (target == CaseState.QC_FAILED) {
            c.transition(CaseState.QC_FAILED, Actor.system());
            return c;
        }

        c.transition(CaseState.QC_APPROVED, Actor.system());
        if (target == CaseState.QC_APPROVED) return c;

        c.transition(CaseState.WAITING_NOTARY_APPROVAL, STAFF);
        if (target == CaseState.WAITING_NOTARY_APPROVAL) return c;

        c.transition(CaseState.FINALIZED, NOTARIS);
        c.assignNomorAkta(com.notarist.core.domain.valueobject.NomorAkta.of("1/VII/2026"));
        if (target == CaseState.FINALIZED) return c;

        c.transition(CaseState.DELIVERED, STAFF);
        if (target == CaseState.DELIVERED) return c;

        c.transition(CaseState.ARCHIVED, Actor.system());
        return c;
    }

    // ---- The happy path ------------------------------------------------------------------------

    @Test
    @DisplayName("the full lifecycle CASE_CREATED → ARCHIVED is walkable with legal transitions only")
    void fullLifecycle() {
        Case c = caseAt(CaseState.ARCHIVED);

        assertThat(c.state()).isEqualTo(CaseState.ARCHIVED);
        assertThat(c.isTerminal()).isTrue();
        assertThat(c.closedAt()).isNotNull();
        assertThat(c.nomorAkta()).isNotNull();
    }

    // ---- Illegal transitions are impossible ----------------------------------------------------

    @Nested
    @DisplayName("illegal transitions")
    class Illegal {

        @Test
        @DisplayName("QC cannot be skipped — the notary must never be the first to see an error")
        void cannotSkipQc() {
            Case c = caseAt(CaseState.WAITING_QC);

            assertThatThrownBy(() -> c.transition(CaseState.WAITING_NOTARY_APPROVAL, STAFF))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("a case cannot jump straight to FINALIZED — only a notary's approval creates a deed")
        void cannotJumpToFinalized() {
            Case c = caseAt(CaseState.VERIFIED);

            assertThatThrownBy(() -> c.transition(CaseState.FINALIZED, NOTARIS))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("a FAILED QC is never simply 'approved' — the draft must be regenerated")
        void qcFailedCannotBecomeApproved() {
            Case c = caseAt(CaseState.QC_FAILED);

            assertThatThrownBy(() -> c.transition(CaseState.QC_APPROVED, NOTARIS))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("a signed deed cannot be cancelled — it can only be corrected by a new deed")
        void finalizedCannotBeCancelled() {
            Case c = caseAt(CaseState.FINALIZED);

            assertThatThrownBy(() ->
                    c.transition(CaseState.CANCELLED, ADMIN, TransitionReason.of("changed mind"), null, null))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("verification cannot be skipped — it is the liability boundary")
        void cannotSkipVerification() {
            Case c = caseAt(CaseState.FIELD_EXTRACTION);

            assertThatThrownBy(() -> c.transition(CaseState.VERIFIED, STAFF))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @ParameterizedTest
        @DisplayName("a terminal case accepts NO transition at all")
        @EnumSource(value = CaseState.class, names = {"UPLOADING", "VERIFIED", "FINALIZED", "DELIVERED"})
        void terminalIsFinal(CaseState anyTarget) {
            Case archived = caseAt(CaseState.ARCHIVED);

            assertThatThrownBy(() ->
                    archived.transition(anyTarget, ADMIN, TransitionReason.of("x"), null, null))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("terminal");
        }
    }

    // ---- Authority -----------------------------------------------------------------------------

    @Nested
    @DisplayName("authority")
    class Authority {

        @Test
        @DisplayName("only a NOTARIS may finalize — not ADMIN, not even PIMPINAN")
        void onlyNotaryMayFinalize() {
            assertThatThrownBy(() -> caseAt(CaseState.WAITING_NOTARY_APPROVAL).transition(CaseState.FINALIZED, ADMIN))
                    .isInstanceOf(AuthorityException.class);

            assertThatThrownBy(() -> caseAt(CaseState.WAITING_NOTARY_APPROVAL).transition(CaseState.FINALIZED, PIMPINAN))
                    .as("notarial authority is statutory and personal — it cannot be escalated upward")
                    .isInstanceOf(AuthorityException.class);

            assertThatThrownBy(() -> caseAt(CaseState.WAITING_NOTARY_APPROVAL).transition(CaseState.FINALIZED, STAFF))
                    .isInstanceOf(AuthorityException.class);

            assertThatCode(() -> caseAt(CaseState.WAITING_NOTARY_APPROVAL).transition(CaseState.FINALIZED, NOTARIS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the SYSTEM may not perform a human act — verification is the liability boundary")
        void systemMayNotVerify() {
            Case c = caseAt(CaseState.WAITING_VERIFICATION);

            assertThatThrownBy(() -> c.transition(CaseState.VERIFIED, Actor.system()))
                    .isInstanceOf(AuthorityException.class);
        }

        @Test
        @DisplayName("a human may not perform a SYSTEM transition (the pipeline drives OCR, not staff)")
        void humanMayNotDrivePipeline() {
            Case c = caseAt(CaseState.OCR_RUNNING);

            assertThatThrownBy(() -> c.transition(CaseState.FIELD_EXTRACTION, STAFF))
                    .isInstanceOf(AuthorityException.class);
        }

        @Test
        @DisplayName("a case cannot be opened by the system — someone is accountable for the work")
        void systemCannotOpenCase() {
            assertThatThrownBy(() -> CaseFactory.create(
                    CaseNumber.of("2/VII/2026"), CaseType.APHT, TENANT, Actor.system(), null, null, null))
                    .isInstanceOf(AuthorityException.class);
        }
    }

    // ---- Retry, rollback, reasons --------------------------------------------------------------

    @Nested
    @DisplayName("retry and rollback")
    class RetryRollback {

        @Test
        @DisplayName("OCR_FAILED may retry back into OCR_RUNNING")
        void ocrRetry() {
            Case c = caseAt(CaseState.OCR_FAILED);
            c.transition(CaseState.OCR_RUNNING, STAFF);

            assertThat(c.state()).isEqualTo(CaseState.OCR_RUNNING);
            assertThat(CaseStateMachine.edge(CaseState.OCR_FAILED, CaseState.OCR_RUNNING))
                    .get().extracting(CaseStateMachine.Edge::kind).isEqualTo(TransitionKind.RETRY);
        }

        @Test
        @DisplayName("QC_FAILED can roll back to EITHER drafting or verification — the hinge of the workflow")
        void qcFailedOffersBothRollbacks() {
            // The draft was wrong → regenerate.
            Case a = caseAt(CaseState.QC_FAILED);
            a.transition(CaseState.GENERATING_DRAFT, STAFF, TransitionReason.of("draft salah"), null, null);
            assertThat(a.state()).isEqualTo(CaseState.GENERATING_DRAFT);

            // The source facts were wrong → re-verify. Choosing between these requires human judgement,
            // so the machine offers both and refuses to pick.
            Case b = caseAt(CaseState.QC_FAILED);
            b.transition(CaseState.WAITING_VERIFICATION, STAFF, TransitionReason.of("NIK salah"), null, null);
            assertThat(b.state()).isEqualTo(CaseState.WAITING_VERIFICATION);
        }

        @Test
        @DisplayName("a ROLLBACK without a reason is REJECTED — a regulator will ask why")
        void rollbackRequiresReason() {
            Case c = caseAt(CaseState.QC_FAILED);

            assertThatThrownBy(() ->
                    c.transition(CaseState.GENERATING_DRAFT, STAFF, TransitionReason.NONE, null, null))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("requires a reason");

            assertThatThrownBy(() -> c.transition(CaseState.GENERATING_DRAFT, STAFF))
                    .isInstanceOf(InvariantViolationException.class);
        }

        @Test
        @DisplayName("a CANCEL without a reason is REJECTED")
        void cancelRequiresReason() {
            Case c = caseAt(CaseState.UPLOADING);

            assertThatThrownBy(() -> c.transition(CaseState.CANCELLED, ADMIN))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("requires a reason");

            assertThatCode(() -> c.transition(
                    CaseState.CANCELLED, ADMIN, TransitionReason.of("klien membatalkan"), null, null))
                    .doesNotThrowAnyException();
            assertThat(c.state()).isEqualTo(CaseState.CANCELLED);
        }

        @Test
        @DisplayName("a case cannot be cancelled once the notary is involved")
        void cannotCancelAfterQcApproved() {
            Case c = caseAt(CaseState.WAITING_NOTARY_APPROVAL);

            assertThatThrownBy(() ->
                    c.transition(CaseState.CANCELLED, ADMIN, TransitionReason.of("x"), null, null))
                    .isInstanceOf(IllegalTransitionException.class);
        }
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Nested
    @DisplayName("invariants")
    class Invariants {

        @Test
        @DisplayName("a nomor akta cannot be assigned before FINALIZED")
        void nomorAktaOnlyAtFinalized() {
            Case c = caseAt(CaseState.WAITING_NOTARY_APPROVAL);

            assertThatThrownBy(() ->
                    c.assignNomorAkta(com.notarist.core.domain.valueobject.NomorAkta.of("9/VII/2026")))
                    .isInstanceOf(InvariantViolationException.class);
        }

        @Test
        @DisplayName("a nomor akta is assigned exactly once — never reissued")
        void nomorAktaAssignedOnce() {
            Case c = caseAt(CaseState.FINALIZED);

            assertThatThrownBy(() ->
                    c.assignNomorAkta(com.notarist.core.domain.valueobject.NomorAkta.of("2/VII/2026")))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("exactly once");
        }

        @Test
        @DisplayName("a case cannot be DELIVERED without a repertorium number")
        void deliveredRequiresNomorAkta() {
            Case c = newCase();
            c.transition(CaseState.UPLOADING, STAFF);
            c.transition(CaseState.OCR_RUNNING, STAFF);
            c.transition(CaseState.FIELD_EXTRACTION, Actor.system());
            c.transition(CaseState.WAITING_VERIFICATION, Actor.system());
            c.transition(CaseState.VERIFIED, STAFF);
            c.transition(CaseState.GENERATING_DRAFT, STAFF);
            c.transition(CaseState.WAITING_QC, Actor.system());
            c.transition(CaseState.QC_APPROVED, Actor.system());
            c.transition(CaseState.WAITING_NOTARY_APPROVAL, STAFF);
            c.transition(CaseState.FINALIZED, NOTARIS);
            // deliberately NOT allocating a nomor akta

            assertThatThrownBy(() -> c.transition(CaseState.DELIVERED, STAFF))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("no nomor akta");
        }
    }

    // ---- Events --------------------------------------------------------------------------------

    @Test
    @DisplayName("every transition raises exactly one CaseTransitioned event, drained once")
    void eventsAreRaisedAndDrainedOnce() {
        Case c = newCase();
        assertThat(c.domainEvents()).hasSize(1);          // CaseCreated

        c.transition(CaseState.UPLOADING, STAFF);
        assertThat(c.domainEvents()).hasSize(2);

        assertThat(c.pullDomainEvents()).hasSize(2);
        assertThat(c.domainEvents()).isEmpty();           // drained — cannot be published twice
        assertThat(c.hasUncommittedEvents()).isFalse();
    }

    @Test
    @DisplayName("the state machine table and the aggregate agree on what is legal")
    void tableAgreesWithAggregate() {
        Case c = caseAt(CaseState.WAITING_QC);

        assertThat(CaseStateMachine.allowedTargets(CaseState.WAITING_QC))
                .containsExactlyInAnyOrder(CaseState.QC_APPROVED, CaseState.QC_FAILED, CaseState.CANCELLED);
        assertThat(c.canTransitionTo(CaseState.QC_APPROVED)).isTrue();
        assertThat(c.canTransitionTo(CaseState.FINALIZED)).isFalse();
    }
}
