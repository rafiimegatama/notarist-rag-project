package com.notarist.kase.domain.factory;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.valueobject.*;

/**
 * Creates a Bundle for a Case.
 *
 * <p>Takes the {@link Case} rather than a bare CaseId so it can refuse to open a bundle on a terminal
 * case — a rule that would otherwise be enforced nowhere, since Bundle is its own aggregate and cannot
 * see the case's state.
 */
public final class BundleFactory {

    private BundleFactory() {}

    public static Bundle create(Case aCase, BundleType bundleType, int expectedDocumentCount,
                                Actor actor, CorrelationId correlationId, TraceId traceId) {

        if (aCase.isTerminal()) {
            throw new InvariantViolationException(
                    "Cannot open a bundle on a " + aCase.state() + " case");
        }

        Bundle bundle = Bundle.open(BundleId.generate(), aCase.caseId(), bundleType,
                aCase.tenantId(), expectedDocumentCount, actor, correlationId, traceId);

        aCase.attachBundle(bundle.bundleId());
        return bundle;
    }
}
