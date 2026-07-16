package com.notarist.kase.infrastructure.persistence.mapper;

import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.infrastructure.persistence.postgres.BundleWorkflowJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Translates between the {@link BundleWorkflow} aggregate and its JPA row. */
@Component
public class BundleWorkflowMapper {

    public BundleWorkflow toDomain(BundleWorkflowJpaEntity e) {
        return BundleWorkflow.rehydrate(
                BundleId.of(UUID.fromString(e.getBundleId())),
                CaseId.of(UUID.fromString(e.getCaseId())),
                UUID.fromString(e.getTenantId()),
                BundleWorkflowStatus.valueOf(e.getStatus()),
                e.getCreatedAt());
    }

    public BundleWorkflowJpaEntity toNewEntity(BundleWorkflow w) {
        return new BundleWorkflowJpaEntity(
                w.bundleId().value().toString(),
                w.caseId().value().toString(),
                w.tenantId().toString(),
                w.status().name(),
                w.createdAt());
    }
}
