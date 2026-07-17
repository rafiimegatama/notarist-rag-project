package com.notarist.verification.infrastructure;

import com.notarist.verification.application.port.out.VerificationFactsPort;
import com.notarist.verification.domain.service.VerificationFacts;
import com.notarist.verification.domain.valueobject.BundleId;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link VerificationFactsPort}: returns empty facts, so every automatic check evaluates to
 * MANUAL_REQUIRED (or NOT_APPLICABLE) rather than silently passing. It fails safe until the
 * composition root wires an adapter that reads real OCR-review output.
 *
 * <p>This carried {@code @ConditionalOnMissingBean} to let a production adapter replace it. That
 * annotation does nothing useful on a component-scanned class — Spring Boot evaluates it against a
 * bean factory that is still being populated by the scan, which is why it is documented as
 * auto-configuration-only. The effect here was the opposite of the intent: the default was never
 * registered at all, and VerificationApplicationService failed the context with "No qualifying bean
 * of type VerificationFactsPort".
 *
 * <p>Registering unconditionally is safe because this is the only implementation. A production
 * adapter must be marked {@code @Primary}, or this bean moved to an {@code @Bean} method on a
 * configuration class where {@code @ConditionalOnMissingBean} is actually honoured.
 */
@Component
public class VerificationFactsPortDefaultAdapter implements VerificationFactsPort {

    @Override
    public VerificationFacts factsFor(BundleId bundleId, UUID tenantId) {
        return VerificationFacts.builder().build();
    }
}
