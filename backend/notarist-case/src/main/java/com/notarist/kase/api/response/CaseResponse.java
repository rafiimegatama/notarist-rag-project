package com.notarist.kase.api.response;

import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.valueobject.BundleId;

import java.util.List;
import java.util.UUID;

/** Read model for a single case. Built from the aggregate; carries no behaviour. */
public record CaseResponse(
        UUID caseId,
        String caseNumber,
        String caseType,
        UUID tenantId,
        UUID createdBy,
        UUID assignedNotarisId,
        String state,
        boolean terminal,
        String nomorAkta,
        List<UUID> bundleIds,
        String createdAt,
        String closedAt
) {
    public static CaseResponse from(Case c) {
        return new CaseResponse(
                c.caseId().value(),
                c.caseNumber().value(),
                c.caseType().name(),
                c.tenantId(),
                c.createdBy(),
                c.assignedNotarisId(),
                c.state().name(),
                c.isTerminal(),
                c.nomorAkta() != null ? c.nomorAkta().value() : null,
                c.bundleIds().stream().map(BundleId::value).toList(),
                c.createdAt() != null ? c.createdAt().toString() : null,
                c.closedAt() != null ? c.closedAt().toString() : null);
    }
}
