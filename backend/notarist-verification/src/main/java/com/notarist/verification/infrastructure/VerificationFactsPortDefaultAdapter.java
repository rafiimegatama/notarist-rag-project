package com.notarist.verification.infrastructure;

import com.notarist.verification.application.port.out.VerificationFactsPort;
import com.notarist.verification.domain.service.VerificationFacts;
import com.notarist.verification.domain.valueobject.BundleId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link VerificationFactsPort}: returns empty facts, so every automatic check evaluates to
 * MANUAL_REQUIRED (or NOT_APPLICABLE) rather than silently passing. It fails safe until the
 * composition root wires an adapter that reads real OCR-review output.
 *
 * <p>{@code @ConditionalOnMissingBean} so a production adapter registered elsewhere transparently
 * replaces it without touching this module.
 */
@Component
@ConditionalOnMissingBean(VerificationFactsPort.class)
public class VerificationFactsPortDefaultAdapter implements VerificationFactsPort {

    @Override
    public VerificationFacts factsFor(BundleId bundleId, UUID tenantId) {
        return VerificationFacts.builder().build();
    }
}
