package com.notarist.verification.infrastructure.event;

import com.notarist.core.api.event.VerificationProvisioningRequested;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.verification.application.command.InitializeVerificationCommand;
import com.notarist.verification.application.port.in.VerificationProvisioningUseCase;
import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Provisions the verification checklist automatically when a bundle becomes ready to be verified. This
 * is the production caller {@link VerificationProvisioningUseCase} was built to have; before this,
 * {@code initializeVerification} had no runtime driver.
 *
 * <h3>Why AFTER_COMMIT + REQUIRES_NEW</h3>
 * The trigger event is published on the same transaction as the bundle-workflow transition (see the
 * bridge in notarist-case). Consuming it at {@link TransactionPhase#AFTER_COMMIT} means the checklist is
 * created only once that transition has durably committed. At that phase the publishing transaction has
 * already committed but its synchronizations are still unwinding, so a plain {@code REQUIRED} write
 * would join a completing transaction and never commit. This method opens its own {@code REQUIRES_NEW}
 * transaction, inside which the facts port reads the OCR-review output and the checklist rows are
 * written.
 *
 * <h3>Tenant identity + RLS</h3>
 * The {@code verification} table is under fail-closed, FORCE'd RLS (Flyway V13/V14). This listener runs
 * synchronously after commit, possibly still on the request thread that drove the transition, so it
 * saves and restores whatever principal is present and installs the event's own tenant for the duration
 * of the provisioning. That gives the facts-port read and the checklist insert the correct tenant
 * identity without opening a system-wide RLS bypass and without clobbering the caller's context.
 *
 * <h3>Idempotency</h3>
 * A duplicate (re-entering READY_FOR_VERIFICATION after a QC bounce, or a redelivered event) is
 * rejected by {@code initializeVerification} with {@link VerificationInvariantViolationException} and
 * handled here as a no-op.
 */
@Component
public class VerificationProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationProvisioningListener.class);

    private static final String SYSTEM_ROLE = "SYSTEM";

    private final VerificationProvisioningUseCase provisioningUseCase;

    public VerificationProvisioningListener(VerificationProvisioningUseCase provisioningUseCase) {
        this.provisioningUseCase = provisioningUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVerificationProvisioningRequested(VerificationProvisioningRequested event) {
        Optional<VpdContextHolder.VpdPrincipal> previous = VpdContextHolder.get();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                event.actorUserId(), event.tenantId(), SYSTEM_ROLE));
        try {
            provisioningUseCase.initializeVerification(new InitializeVerificationCommand(
                    event.bundleId(),
                    event.tenantId(),
                    null));   // no HTTP caller; this is a system-initiated provisioning
            log.info("Provisioned verification for bundle {} (tenant {})",
                    event.bundleId(), event.tenantId());
        } catch (VerificationInvariantViolationException alreadyExists) {
            log.debug("Verification already exists for bundle {} — provisioning skipped (idempotent)",
                    event.bundleId());
        } catch (RuntimeException ex) {
            log.error("Failed to provision verification for bundle {}: {}",
                    event.bundleId(), ex.getMessage(), ex);
        } finally {
            previous.ifPresentOrElse(VpdContextHolder::set, VpdContextHolder::clear);
        }
    }
}
