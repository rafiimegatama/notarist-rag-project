package com.notarist.kase.infrastructure.persistence.mapper;

import com.notarist.core.domain.valueobject.NomorAkta;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.CaseNumber;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.infrastructure.persistence.postgres.CaseJpaEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Translates between the {@link Case} aggregate and its JPA row. No business logic here. */
@Component
public class CaseMapper {

    public Case toDomain(CaseJpaEntity e) {
        List<BundleId> bundleIds = e.getBundleIds().stream()
                .map(id -> BundleId.of(UUID.fromString(id)))
                .toList();

        return Case.rehydrate(
                CaseId.of(UUID.fromString(e.getCaseId())),
                CaseNumber.of(e.getCaseNumber()),
                CaseType.valueOf(e.getCaseType()),
                UUID.fromString(e.getTenantId()),
                UUID.fromString(e.getCreatedBy()),
                e.getAssignedNotarisId() != null ? UUID.fromString(e.getAssignedNotarisId()) : null,
                CaseState.valueOf(e.getState()),
                bundleIds,
                e.getNomorAkta() != null ? NomorAkta.of(e.getNomorAkta()) : null,
                e.getCreatedAt(),
                e.getClosedAt());
    }

    /** A fresh (unmanaged) entity for the INSERT path. Updates go through the managed entity instead. */
    public CaseJpaEntity toNewEntity(Case c) {
        return new CaseJpaEntity(
                c.caseId().value().toString(),
                c.caseNumber().value(),
                c.caseType().name(),
                c.tenantId().toString(),
                c.createdBy().toString(),
                c.assignedNotarisId() != null ? c.assignedNotarisId().toString() : null,
                c.state().name(),
                c.nomorAkta() != null ? c.nomorAkta().value() : null,
                c.createdAt(),
                c.closedAt(),
                bundleIdStrings(c));
    }

    /** Copies the mutable fields of the aggregate onto an already-managed entity (UPDATE path). */
    public void copyMutableState(Case c, CaseJpaEntity managed) {
        managed.setState(c.state().name());
        managed.setNomorAkta(c.nomorAkta() != null ? c.nomorAkta().value() : null);
        managed.setClosedAt(c.closedAt());
        managed.setAssignedNotarisId(
                c.assignedNotarisId() != null ? c.assignedNotarisId().toString() : null);
        managed.setBundleIds(bundleIdStrings(c));
    }

    private Set<String> bundleIdStrings(Case c) {
        Set<String> ids = new LinkedHashSet<>();
        for (BundleId b : c.bundleIds()) ids.add(b.value().toString());
        return ids;
    }
}
