package com.notarist.kase.infrastructure.persistence.mapper;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.state.BundleStatus;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.BundleType;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.DocumentRef;
import com.notarist.kase.infrastructure.persistence.postgres.BundleDocumentEmbeddable;
import com.notarist.kase.infrastructure.persistence.postgres.BundleJpaEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Translates between the {@link Bundle} composition aggregate and its JPA row. */
@Component
public class BundleMapper {

    public Bundle toDomain(BundleJpaEntity e) {
        List<DocumentRef> docs = e.getDocuments().stream()
                .map(d -> DocumentRef.of(new DocumentId(UUID.fromString(d.getDocumentId())), d.getRoleInBundle()))
                .toList();
        return Bundle.rehydrate(
                BundleId.of(UUID.fromString(e.getBundleId())),
                CaseId.of(UUID.fromString(e.getCaseId())),
                BundleType.valueOf(e.getBundleType()),
                UUID.fromString(e.getTenantId()),
                e.getExpectedDocumentCount(),
                BundleStatus.valueOf(e.getAssemblyStatus()),
                docs,
                e.getCreatedAt());
    }

    public BundleJpaEntity toNewEntity(Bundle b) {
        return new BundleJpaEntity(
                b.bundleId().value().toString(),
                b.caseId().value().toString(),
                b.tenantId().toString(),
                b.bundleType().name(),
                b.expectedDocumentCount(),
                b.status().name(),
                b.createdAt(),
                documentRows(b));
    }

    public void copyMutableState(Bundle b, BundleJpaEntity managed) {
        managed.setAssemblyStatus(b.status().name());
        managed.setDocuments(documentRows(b));
    }

    private Set<BundleDocumentEmbeddable> documentRows(Bundle b) {
        Set<BundleDocumentEmbeddable> rows = new LinkedHashSet<>();
        for (DocumentRef ref : b.documents()) {
            rows.add(new BundleDocumentEmbeddable(ref.documentId().value().toString(), ref.roleInBundle()));
        }
        return rows;
    }
}
