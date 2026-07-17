package com.notarist.kase.domain.factory;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.model.Approval;
import com.notarist.kase.domain.valueobject.ApprovalType;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.valueobject.ApprovalId;
import com.notarist.kase.domain.valueobject.Role;

import java.util.UUID;

/**
 * Raises an approval request against a ROLE.
 *
 * <p>The required role is derived from the {@link ApprovalType}, not passed in by the caller — the
 * authority rules belong to the domain, and a caller that could choose the required role could choose
 * an easier one.
 */
public final class ApprovalFactory {

    private ApprovalFactory() {}

    public static Approval request(Case aCase, ApprovalType type, UUID submittedBy,
                                   CorrelationId correlationId, TraceId traceId) {

        Role requiredRole = type == ApprovalType.NOTARY_SIGNATURE ? Role.NOTARIS : Role.PIMPINAN;

        return Approval.request(ApprovalId.generate(), aCase.caseId(), type, requiredRole,
                aCase.tenantId(), submittedBy, correlationId, traceId);
    }
}
