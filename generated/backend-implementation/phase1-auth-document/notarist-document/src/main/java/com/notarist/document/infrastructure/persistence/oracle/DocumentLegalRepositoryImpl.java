package com.notarist.document.infrastructure.persistence.oracle;

import com.notarist.document.infrastructure.security.VpdContextApplier;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PHASE 6A.2-FIX:
 *   - findByTenantId / countByTenantId: pass enums directly (no .name()), use
 *     List<ClassificationLevel> for allowedLevels, ClassificationLevel for maxClearance sentinel
 *   - updateStatus: entity.setStatus() now takes DocumentStatus enum directly
 *   - computeAllowedLevels: returns List<ClassificationLevel> (no Enum::name mapping)
 */
@Repository
public class DocumentLegalRepositoryImpl implements DocumentLegalRepository {

    private final DocumentLegalJpaRepository jpaRepository;
    private final DocumentLegalMapper mapper;
    private final VpdContextApplier vpdContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public DocumentLegalRepositoryImpl(
            DocumentLegalJpaRepository jpaRepository,
            DocumentLegalMapper mapper,
            VpdContextApplier vpdContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.vpdContextApplier = vpdContextApplier;
    }

    @Override
    public Optional<DocumentLegal> findById(DocumentId documentId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(documentId.value().toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<DocumentLegal> findByChecksumAndTenantId(String checksumSha256, UUID tenantId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByChecksumSha256AndTenantId(checksumSha256, tenantId.toString())
                .map(mapper::toDomain);
    }

    @Override
    public List<DocumentLegal> findByTenantId(UUID tenantId, DocumentFilter filter, int page, int size) {
        vpdContextApplier.applyIfPresent(entityManager);
        JenisDokumen docType = filter.documentType();
        DocumentStatus status = filter.status();
        ClassificationLevel maxClearance = filter.maxClearance();
        List<ClassificationLevel> allowedLevels = computeAllowedLevels(maxClearance);

        return jpaRepository.findByTenantIdWithFilters(
                        tenantId.toString(), docType, status,
                        maxClearance, allowedLevels,
                        PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByTenantId(UUID tenantId, DocumentFilter filter) {
        vpdContextApplier.applyIfPresent(entityManager);
        JenisDokumen docType = filter.documentType();
        DocumentStatus status = filter.status();
        ClassificationLevel maxClearance = filter.maxClearance();
        List<ClassificationLevel> allowedLevels = computeAllowedLevels(maxClearance);

        return jpaRepository.countByTenantIdWithFilters(
                tenantId.toString(), docType, status, maxClearance, allowedLevels);
    }

    @Override
    public void save(DocumentLegal document) {
        vpdContextApplier.applyIfPresent(entityManager);
        jpaRepository.save(mapper.toEntity(document));
    }

    @Override
    public void updateStatus(DocumentId documentId, DocumentStatus status) {
        vpdContextApplier.applyIfPresent(entityManager);
        jpaRepository.findById(documentId.value().toString()).ifPresent(entity -> {
            entity.setStatus(status);
            jpaRepository.save(entity);
        });
    }

    private List<ClassificationLevel> computeAllowedLevels(ClassificationLevel maxClearance) {
        if (maxClearance == null) return List.of();
        return Arrays.stream(ClassificationLevel.values())
                .filter(level -> !level.exceeds(maxClearance))
                .toList();
    }
}
