package com.notarist.kase.api.response;

import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.model.BundleWorkflow;

import java.util.UUID;

/** Read model for a bundle — composition facts from {@link Bundle}, lifecycle from {@link BundleWorkflow}. */
public record BundleResponse(
        UUID bundleId,
        UUID caseId,
        UUID tenantId,
        String bundleType,
        int expectedDocumentCount,
        int documentCount,
        String assemblyStatus,
        String workflowStatus,
        boolean terminal,
        String createdAt
) {
    public static BundleResponse from(Bundle bundle, BundleWorkflow workflow) {
        return new BundleResponse(
                bundle.bundleId().value(),
                bundle.caseId().value(),
                bundle.tenantId(),
                bundle.bundleType().name(),
                bundle.expectedDocumentCount(),
                bundle.documentCount(),
                bundle.status().name(),
                workflow.status().name(),
                workflow.isTerminal(),
                bundle.createdAt() != null ? bundle.createdAt().toString() : null);
    }
}
