package com.notarist.kase.infrastructure.event;

import com.notarist.core.api.event.VerificationProvisioningRequested;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.BundleWorkflowTransitioned;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VerificationProvisioningBridgeTest {

    private ApplicationEventPublisher publisher;
    private VerificationProvisioningBridge bridge;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        bridge = new VerificationProvisioningBridge(publisher);
    }

    private BundleWorkflowTransitioned transition(
            BundleId bundleId, UUID tenantId, UUID actorUserId,
            BundleWorkflowStatus from, BundleWorkflowStatus to) {
        return new BundleWorkflowTransitioned(
                bundleId, CaseId.generate(), from, to, tenantId,
                Actor.of(actorUserId, Role.NOTARIS),
                CorrelationId.generate(), TraceId.generate());
    }

    @Test
    @DisplayName("READY_FOR_VERIFICATION triggers a provisioning request carrying bundle, tenant, actor")
    void publishesOnReadyForVerification() {
        BundleId bundleId = BundleId.generate();
        UUID tenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        bridge.onBundleWorkflowTransitioned(transition(bundleId, tenantId, actorUserId,
                BundleWorkflowStatus.COLLECTING_DOCUMENTS, BundleWorkflowStatus.READY_FOR_VERIFICATION));

        ArgumentCaptor<VerificationProvisioningRequested> captor =
                ArgumentCaptor.forClass(VerificationProvisioningRequested.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().bundleId()).isEqualTo(bundleId.value());
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().actorUserId()).isEqualTo(actorUserId);
    }

    @Test
    @DisplayName("any other transition is ignored — verification is provisioned once, at the ready gate")
    void ignoresOtherTransitions() {
        bridge.onBundleWorkflowTransitioned(transition(
                BundleId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                BundleWorkflowStatus.READY_FOR_VERIFICATION, BundleWorkflowStatus.UNDER_VERIFICATION));

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
