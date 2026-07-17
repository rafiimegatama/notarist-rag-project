package com.notarist.verification.infrastructure.event;

import com.notarist.core.api.event.VerificationProvisioningRequested;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.verification.application.command.InitializeVerificationCommand;
import com.notarist.verification.application.port.in.VerificationProvisioningUseCase;
import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
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

class VerificationProvisioningListenerTest {

    private VerificationProvisioningUseCase useCase;
    private VerificationProvisioningListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(VerificationProvisioningUseCase.class);
        listener = new VerificationProvisioningListener(useCase);
        VpdContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    private VerificationProvisioningRequested event(UUID bundleId, UUID tenantId, UUID actor) {
        return new VerificationProvisioningRequested(bundleId, tenantId, actor);
    }

    @Test
    @DisplayName("maps the bundle-ready event onto initializeVerification")
    void mapsEventOntoCommand() {
        UUID bundleId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        listener.onVerificationProvisioningRequested(event(bundleId, tenantId, UUID.randomUUID()));

        ArgumentCaptor<InitializeVerificationCommand> captor =
                ArgumentCaptor.forClass(InitializeVerificationCommand.class);
        verify(useCase).initializeVerification(captor.capture());
        assertThat(captor.getValue().bundleId()).isEqualTo(bundleId);
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("installs the event's tenant during the call and restores the prior principal after")
    void installsTenantAndRestoresPreviousPrincipal() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        // A caller principal is already on the thread (this listener can run on the request thread).
        VpdContextHolder.VpdPrincipal caller =
                new VpdContextHolder.VpdPrincipal(UUID.randomUUID(), UUID.randomUUID(), "NOTARIS");
        VpdContextHolder.set(caller);

        AtomicReference<UUID> tenantSeenDuringCall = new AtomicReference<>();
        when(useCase.initializeVerification(any())).thenAnswer(inv -> {
            tenantSeenDuringCall.set(VpdContextHolder.get()
                    .map(VpdContextHolder.VpdPrincipal::tenantId).orElse(null));
            return null;
        });

        listener.onVerificationProvisioningRequested(event(UUID.randomUUID(), tenantId, actor));

        assertThat(tenantSeenDuringCall.get()).isEqualTo(tenantId);
        // The caller's context must be intact afterwards — not cleared, not overwritten.
        assertThat(VpdContextHolder.get()).contains(caller);
    }

    @Test
    @DisplayName("a pre-existing verification is an idempotent no-op")
    void idempotentWhenVerificationAlreadyExists() {
        when(useCase.initializeVerification(any()))
                .thenThrow(new VerificationInvariantViolationException("already exists"));

        assertThatCode(() -> listener.onVerificationProvisioningRequested(
                event(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .doesNotThrowAnyException();
    }
}
