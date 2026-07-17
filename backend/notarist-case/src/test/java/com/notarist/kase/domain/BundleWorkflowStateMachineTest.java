package com.notarist.kase.domain;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.state.BundleWorkflowStateMachine;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleWorkflowStateMachineTest {

    private final UUID tenantId = UUID.randomUUID();
    private final Actor staff = Actor.of(UUID.randomUUID(), Role.STAFF);

    private BundleWorkflow workflowAt(BundleWorkflowStatus status) {
        return BundleWorkflow.rehydrate(BundleId.generate(), CaseId.generate(), tenantId, status,
                java.time.Instant.now());
    }

    @Test
    @DisplayName("the happy path walks OPEN → … → DELIVERED → LOCKED")
    void happyPath() {
        BundleWorkflow w = BundleWorkflow.start(BundleId.generate(), CaseId.generate(), tenantId);
        w.transition(BundleWorkflowStatus.COLLECTING_DOCUMENTS, staff, null, null);
        w.transition(BundleWorkflowStatus.READY_FOR_VERIFICATION, staff, null, null);
        w.transition(BundleWorkflowStatus.UNDER_VERIFICATION, staff, null, null);
        w.transition(BundleWorkflowStatus.READY_FOR_QC, staff, null, null);
        w.transition(BundleWorkflowStatus.QC_PASSED, staff, null, null);
        w.transition(BundleWorkflowStatus.READY_FOR_DELIVERY, staff, null, null);
        w.transition(BundleWorkflowStatus.DELIVERED, staff, null, null);
        w.transition(BundleWorkflowStatus.LOCKED, staff, null, null);
        assertThat(w.status()).isEqualTo(BundleWorkflowStatus.LOCKED);
        assertThat(w.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("QC can fail and rework back to verification or re-QC")
    void qcRework() {
        assertThat(BundleWorkflowStateMachine.isLegal(
                BundleWorkflowStatus.READY_FOR_QC, BundleWorkflowStatus.QC_FAILED)).isTrue();
        assertThat(BundleWorkflowStateMachine.isLegal(
                BundleWorkflowStatus.QC_FAILED, BundleWorkflowStatus.READY_FOR_QC)).isTrue();
        assertThat(BundleWorkflowStateMachine.isLegal(
                BundleWorkflowStatus.QC_FAILED, BundleWorkflowStatus.READY_FOR_VERIFICATION)).isTrue();
    }

    @Test
    @DisplayName("an edge not in the table is rejected by the aggregate")
    void illegalEdge() {
        BundleWorkflow w = workflowAt(BundleWorkflowStatus.OPEN);
        assertThatThrownBy(() -> w.transition(BundleWorkflowStatus.DELIVERED, staff, null, null))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    @DisplayName("LOCKED is terminal — no further move")
    void lockedTerminal() {
        BundleWorkflow w = workflowAt(BundleWorkflowStatus.LOCKED);
        assertThat(BundleWorkflowStateMachine.allowedTargets(BundleWorkflowStatus.LOCKED)).isEmpty();
        assertThatThrownBy(() -> w.transition(BundleWorkflowStatus.DELIVERED, staff, null, null))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    @DisplayName("SYSTEM may not drive the human bundle workflow")
    void systemRejected() {
        BundleWorkflow w = workflowAt(BundleWorkflowStatus.OPEN);
        assertThatThrownBy(() -> w.transition(
                BundleWorkflowStatus.COLLECTING_DOCUMENTS, Actor.system(), null, null))
                .isInstanceOf(AuthorityException.class);
    }

    @Test
    @DisplayName("a transition raises BundleWorkflowTransitioned carrying from/to")
    void raisesEvent() {
        BundleWorkflow w = workflowAt(BundleWorkflowStatus.OPEN);
        w.transition(BundleWorkflowStatus.COLLECTING_DOCUMENTS, staff,
                CorrelationId.generate(), null);
        assertThat(w.domainEvents()).hasSize(1);
        assertThat(w.domainEvents().get(0).eventType()).isEqualTo("BundleWorkflowTransitioned");
    }
}
