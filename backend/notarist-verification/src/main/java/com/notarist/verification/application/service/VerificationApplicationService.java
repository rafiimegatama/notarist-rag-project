package com.notarist.verification.application.service;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.verification.api.response.VerificationResponse;
import com.notarist.verification.api.response.VerificationSummaryResponse;
import com.notarist.verification.application.command.ChangeVerificationStatusCommand;
import com.notarist.verification.application.command.InitializeVerificationCommand;
import com.notarist.verification.application.command.UpdateChecklistItemCommand;
import com.notarist.verification.application.port.in.VerificationProvisioningUseCase;
import com.notarist.verification.application.port.in.VerificationUseCase;
import com.notarist.verification.application.port.out.DomainEventPublisher;
import com.notarist.verification.application.port.out.VerificationFactsPort;
import com.notarist.verification.application.port.out.VerificationRepository;
import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
import com.notarist.verification.domain.exception.VerificationNotFoundException;
import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.service.AutomaticCheckEvaluator;
import com.notarist.verification.domain.service.AutomaticCheckResult;
import com.notarist.verification.domain.service.VerificationFacts;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.VerificationId;
import com.notarist.verification.infrastructure.event.VerificationAuditEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the verification use cases. Holds NO business rules of its own: it loads the aggregate,
 * asks it to do the work ({@code verification.decideItem(...)}, {@code verification.changeStatus(...)}),
 * persists the result, then publishes the events the aggregate raised. The completion rule, transition
 * legality and authority all live inside the aggregate — never here.
 *
 * <p>Every write is one transaction, so the verification, its checklist decisions and the append-only
 * audit rows commit together, the optimistic lock guards concurrent verifiers, and events are
 * published only after a successful save.
 */
@Service
@Transactional
public class VerificationApplicationService
        implements VerificationUseCase, VerificationProvisioningUseCase {

    private final VerificationRepository repository;
    private final VerificationFactsPort factsPort;
    private final DomainEventPublisher eventPublisher;
    private final VerificationAuditEventPublisher auditPublisher;
    private final AutomaticCheckEvaluator evaluator = new AutomaticCheckEvaluator();

    public VerificationApplicationService(VerificationRepository repository,
                                          VerificationFactsPort factsPort,
                                          DomainEventPublisher eventPublisher,
                                          VerificationAuditEventPublisher auditPublisher) {
        this.repository = repository;
        this.factsPort = factsPort;
        this.eventPublisher = eventPublisher;
        this.auditPublisher = auditPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationResponse getVerification(BundleId bundleId, CallerContext caller) {
        return VerificationResponse.from(loadForCaller(bundleId, caller));
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationSummaryResponse getSummary(BundleId bundleId, CallerContext caller) {
        return VerificationSummaryResponse.from(loadForCaller(bundleId, caller));
    }

    @Override
    public VerificationResponse updateChecklistItem(UpdateChecklistItemCommand command) {
        CallerContext caller = command.caller();
        Verification verification = loadForCaller(command.bundleId(), caller);
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        verification.decideItem(command.itemId(), command.decision(), command.comment(),
                caller.asReviewer(), correlationId, traceId);

        repository.save(verification);
        publishEvents(verification.pullDomainEvents());

        auditPublisher.publishItemDecided(
                verification.verificationId().value(), verification.bundleId(),
                caller.userId(), caller.role().name(), caller.tenantId(),
                itemTitle(verification, command.itemId()), command.decision().name(), correlationId);

        return VerificationResponse.from(verification);
    }

    @Override
    public VerificationResponse changeStatus(ChangeVerificationStatusCommand command) {
        CallerContext caller = command.caller();
        Verification verification = loadForCaller(command.bundleId(), caller);
        CorrelationId correlationId = caller.correlationId();
        TraceId traceId = TraceId.generate();

        VerificationStatus from = verification.status();
        verification.changeStatus(command.targetStatus(), caller.asReviewer(), correlationId, traceId);
        VerificationStatus to = verification.status();

        repository.save(verification);
        publishEvents(verification.pullDomainEvents());

        auditPublisher.publishStatusChanged(
                verification.verificationId().value(), verification.bundleId(),
                caller.userId(), caller.role().name(), caller.tenantId(),
                from.name(), to.name(), correlationId);

        return VerificationResponse.from(verification);
    }

    @Override
    public VerificationResponse initializeVerification(InitializeVerificationCommand command) {
        BundleId bundleId = BundleId.of(command.bundleId());
        if (repository.existsByBundleId(bundleId)) {
            throw new VerificationInvariantViolationException(
                    "A verification already exists for bundle " + command.bundleId());
        }

        VerificationFacts facts = factsPort.factsFor(bundleId, command.tenantId());
        List<ChecklistItem> items = new ArrayList<>();
        int order = 0;

        // Automatic checks — computed from OCR-review output, pre-filled with their outcome.
        for (AutomaticCheckResult r : evaluator.evaluateAll(facts)) {
            items.add(ChecklistItem.automatic(ItemId.generate(), r.category(), r.title(),
                    r.mandatory(), order++, r.decision(), r.comment()));
        }

        // Manual observation checks — a human must decide these.
        for (ManualCheck m : MANUAL_CHECKS) {
            items.add(ChecklistItem.create(ItemId.generate(), m.category(), m.title(), m.mandatory(),
                    CheckType.MANUAL, order++));
        }

        Verification verification = Verification.start(
                VerificationId.generate(), command.bundleId(), command.tenantId(), items);
        repository.save(verification);
        return VerificationResponse.from(verification);
    }

    // ---- manual checklist template -------------------------------------------------------------

    private record ManualCheck(ChecklistCategory category, String title, boolean mandatory) {}

    private static final List<ManualCheck> MANUAL_CHECKS = List.of(
            new ManualCheck(ChecklistCategory.SIGNATURE_PRESENCE, "Signature authenticity confirmed", true),
            new ManualCheck(ChecklistCategory.SIGNATURE_PRESENCE, "Seal / stamp authenticity confirmed", true),
            new ManualCheck(ChecklistCategory.IDENTITY, "Party identity documents match the deed", true),
            new ManualCheck(ChecklistCategory.SUPPORTING_DOCUMENTS, "Physical document condition acceptable", false));

    // ---- helpers -------------------------------------------------------------------------------

    /**
     * Loads a verification by bundle and asserts it belongs to the caller's tenant. A cross-tenant hit
     * is audited as a denial and then reported as NOT_FOUND, so the caller cannot learn it exists
     * elsewhere. Defence-in-depth on top of RLS.
     */
    private Verification loadForCaller(BundleId bundleId, CallerContext caller) {
        Verification verification = repository.findByBundleId(bundleId)
                .orElseThrow(() -> new VerificationNotFoundException(
                        "No verification for bundle " + bundleId.value()));
        if (!verification.tenantId().equals(caller.tenantId())) {
            auditPublisher.publishAccessDenied(
                    bundleId.value(), caller.userId(), caller.role().name(),
                    caller.tenantId(), "CROSS_TENANT_ACCESS", caller.correlationId());
            throw new VerificationNotFoundException("No verification for bundle " + bundleId.value());
        }
        return verification;
    }

    private String itemTitle(Verification verification, ItemId itemId) {
        return verification.items().stream()
                .filter(i -> i.itemId().equals(itemId))
                .map(ChecklistItem::title)
                .findFirst()
                .orElse(itemId.toString());
    }

    private void publishEvents(List<com.notarist.core.domain.event.DomainEvent> events) {
        eventPublisher.publishAll(events);
    }
}
