package com.notarist.verification.application;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.verification.api.response.VerificationResponse;
import com.notarist.verification.application.command.ChangeVerificationStatusCommand;
import com.notarist.verification.application.command.InitializeVerificationCommand;
import com.notarist.verification.application.command.UpdateChecklistItemCommand;
import com.notarist.verification.application.port.out.DomainEventPublisher;
import com.notarist.verification.application.port.out.VerificationFactsPort;
import com.notarist.verification.application.port.out.VerificationRepository;
import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.application.service.VerificationApplicationService;
import com.notarist.verification.domain.exception.VerificationNotFoundException;
import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.service.VerificationFacts;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.Role;
import com.notarist.verification.domain.valueobject.VerificationId;
import com.notarist.verification.infrastructure.event.VerificationAuditEventPublisher;
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
class VerificationApplicationServiceTest {

    private VerificationRepository repository;
    private VerificationFactsPort factsPort;
    private DomainEventPublisher events;
    private VerificationAuditEventPublisher audit;
    private VerificationApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private ItemId itemId;

    @BeforeEach
    void setUp() {
        repository = mock(VerificationRepository.class);
        factsPort = mock(VerificationFactsPort.class);
        events = mock(DomainEventPublisher.class);
        audit = mock(VerificationAuditEventPublisher.class);
        service = new VerificationApplicationService(repository, factsPort, events, audit);
    }

    private CallerContext caller(UUID tenant, Role role) {
        return new CallerContext(userId, tenant, role, CorrelationId.generate());
    }

    private Verification verification(UUID tenant) {
        itemId = ItemId.generate();
        ChecklistItem item = ChecklistItem.create(itemId, ChecklistCategory.AUTHORITY, "Authority",
                true, CheckType.MANUAL, 0);
        return Verification.start(VerificationId.generate(), bundleId, tenant, List.of(item));
    }

    @Test
    @DisplayName("getVerification returns the mapped payload for the owning tenant")
    void getHappy() {
        when(repository.findByBundleId(any(BundleId.class))).thenReturn(Optional.of(verification(tenantId)));
        VerificationResponse response = service.getVerification(BundleId.of(bundleId), caller(tenantId, Role.STAFF));
        assertThat(response.bundleId()).isEqualTo(bundleId);
        assertThat(response.checklist()).hasSize(1);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a missing verification is NOT_FOUND")
    void getMissing() {
        when(repository.findByBundleId(any(BundleId.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getVerification(BundleId.of(bundleId), caller(tenantId, Role.STAFF)))
                .isInstanceOf(VerificationNotFoundException.class);
    }

    @Test
    @DisplayName("a cross-tenant verification is reported NOT_FOUND and audited as a denial")
    void getCrossTenant() {
        when(repository.findByBundleId(any(BundleId.class))).thenReturn(Optional.of(verification(UUID.randomUUID())));
        assertThatThrownBy(() -> service.getVerification(BundleId.of(bundleId), caller(tenantId, Role.STAFF)))
                .isInstanceOf(VerificationNotFoundException.class);
        verify(audit).publishAccessDenied(eq(bundleId), eq(userId), anyString(), eq(tenantId), anyString(), any());
    }

    @Test
    @DisplayName("updateChecklistItem saves, publishes events and audits the decision")
    void updateItemHappy() {
        when(repository.findByBundleId(any(BundleId.class))).thenReturn(Optional.of(verification(tenantId)));

        VerificationResponse response = service.updateChecklistItem(new UpdateChecklistItemCommand(
                BundleId.of(bundleId), itemId, Decision.PASS, null, caller(tenantId, Role.STAFF)));

        assertThat(response.status()).isEqualTo("UNDER_VERIFICATION");
        verify(repository, times(1)).save(any(Verification.class));
        verify(events, times(1)).publishAll(any());
        verify(audit, times(1)).publishItemDecided(any(), eq(bundleId), eq(userId), anyString(),
                eq(tenantId), anyString(), eq("PASS"), any());
    }

    @Test
    @DisplayName("an illegal status change is refused by the aggregate and never persisted")
    void changeStatusGuarded() {
        when(repository.findByBundleId(any(BundleId.class))).thenReturn(Optional.of(verification(tenantId)));
        assertThatThrownBy(() -> service.changeStatus(new ChangeVerificationStatusCommand(
                BundleId.of(bundleId), VerificationStatus.VERIFIED, caller(tenantId, Role.NOTARIS))))
                .isInstanceOf(RuntimeException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("initializeVerification builds the automatic + manual checklist from facts and saves it")
    void initialize() {
        when(factsPort.factsFor(any(BundleId.class), eq(tenantId)))
                .thenReturn(VerificationFacts.builder().build());
        when(repository.existsByBundleId(any(BundleId.class))).thenReturn(false);

        VerificationResponse response = service.initializeVerification(
                new InitializeVerificationCommand(bundleId, tenantId, caller(tenantId, Role.STAFF)));

        // 7 automatic checks + 4 manual observation items.
        assertThat(response.checklist()).hasSize(11);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(repository, times(1)).save(any(Verification.class));
    }
}
