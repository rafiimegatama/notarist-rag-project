package com.notarist.document.infrastructure.persistence.postgres;

import com.notarist.document.infrastructure.security.RlsContextApplier;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.document.application.port.out.DocumentLegalRepository;
import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.domain.model.DocumentStatus;
import com.notarist.document.infrastructure.persistence.mapper.DocumentLegalMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The tenant identity is applied at the start of each method; @Transactional guarantees the
 * notarist_set_identity() call and the subsequent JPA query share one PostgreSQL connection and
 * transaction, which is what makes the transaction-local setting visible to the row-level-security
 * policy. PostgreSQL discards the setting at commit/rollback, so it cannot leak to the next
 * borrower of the pooled connection.
 */
@Repository
@Transactional
public class DocumentLegalRepositoryImpl implements DocumentLegalRepository {

    private final DocumentLegalJpaRepository jpaRepository;
    private final DocumentLegalMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public DocumentLegalRepositoryImpl(
            DocumentLegalJpaRepository jpaRepository,
            DocumentLegalMapper mapper,
            RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public Optional<DocumentLegal> findById(DocumentId documentId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(documentId.value().toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<DocumentLegal> findByChecksumAndTenantId(String checksumSha256, UUID tenantId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByChecksumSha256AndTenantId(checksumSha256, tenantId.toString())
                .map(mapper::toDomain);
    }

    @Override
    public List<DocumentLegal> findByTenantId(UUID tenantId, DocumentFilter filter, int page, int size) {
        rlsContextApplier.applyIfPresent(entityManager);
        String docType = filter.documentType() != null ? filter.documentType().name() : null;
        String status = filter.status() != null ? filter.status().name() : null;
        ClassificationLevel maxClearance = filter.maxClearance();
        List<String> allowedLevels = computeAllowedLevels(maxClearance);
        Integer clearanceOrdinal = maxClearance != null ? maxClearance.ordinal() : null;

        return jpaRepository.findByTenantIdWithFilters(
                        tenantId.toString(), docType, status,
                        clearanceOrdinal, allowedLevels,
                        PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(UUID tenantId, DocumentFilter filter) {
        rlsContextApplier.applyIfPresent(entityManager);
        String docType = filter.documentType() != null ? filter.documentType().name() : null;
        String status = filter.status() != null ? filter.status().name() : null;
        ClassificationLevel maxClearance = filter.maxClearance();
        List<String> allowedLevels = computeAllowedLevels(maxClearance);
        Integer clearanceOrdinal = maxClearance != null ? maxClearance.ordinal() : null;

        return jpaRepository.countByTenantIdWithFilters(
                tenantId.toString(), docType, status, clearanceOrdinal, allowedLevels);
    }

    @Override
    public void save(DocumentLegal document) {
        rlsContextApplier.applyIfPresent(entityManager);
        jpaRepository.save(mapper.toEntity(document));
    }

    @Override
    public void updateStatus(DocumentId documentId, DocumentStatus status) {
        rlsContextApplier.applyIfPresent(entityManager);
        jpaRepository.findById(documentId.value().toString()).ifPresent(entity -> {
            entity.setStatus(status.name());
            jpaRepository.save(entity);
        });
    }

    private List<String> computeAllowedLevels(ClassificationLevel maxClearance) {
        if (maxClearance == null) return List.of();
        return Arrays.stream(ClassificationLevel.values())
                .filter(level -> !level.exceeds(maxClearance))
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}
