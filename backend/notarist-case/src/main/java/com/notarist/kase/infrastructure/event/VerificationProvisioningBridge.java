package com.notarist.kase.infrastructure.event;

import com.notarist.core.api.event.VerificationProvisioningRequested;
import com.notarist.kase.domain.event.BundleWorkflowTransitioned;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Translates the case module's bundle-workflow event into the shared, cross-module
 * {@link VerificationProvisioningRequested} so notarist-verification can provision a verification
 * without depending on the case module's domain events.
 *
 * <p><b>Which transition.</b> A verification is bundle-scoped and is seeded when the bundle becomes
 * ready to be verified — the move into {@link BundleWorkflowStatus#READY_FOR_VERIFICATION}. That is the
 * "bundle-ready" trigger the verification provisioning use case was written to wait for; a per-document
 * OCR-review completion is deliberately NOT the trigger, because one bundle spans many documents and
 * the verification covers the bundle as a whole.
 *
 * <p><b>Why a plain {@code @EventListener}.</b> {@link BundleWorkflowTransitioned} is published inside
 * the bundle-transition transaction. Handling it in-transaction and re-publishing the shared event
 * keeps the shared event on the same transaction, so the verification consumer's
 * {@code @TransactionalEventListener(AFTER_COMMIT)} fires only if the transition actually commits. This
 * bridge does no I/O of its own — it only re-labels an event — so it adds no failure surface to the
 * transition.
 */
@Component
public class VerificationProvisioningBridge {

    private static final Logger log = LoggerFactory.getLogger(VerificationProvisioningBridge.class);

    private final ApplicationEventPublisher eventPublisher;

    public VerificationProvisioningBridge(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onBundleWorkflowTransitioned(BundleWorkflowTransitioned event) {
        if (event.toStatus() != BundleWorkflowStatus.READY_FOR_VERIFICATION) {
            return;
        }
        eventPublisher.publishEvent(new VerificationProvisioningRequested(
                event.bundleId().value(),
                event.tenantId(),
                event.actor().userId()));
        log.info("Bundle {} entered READY_FOR_VERIFICATION — requested verification provisioning",
                event.bundleId());
    }
}
