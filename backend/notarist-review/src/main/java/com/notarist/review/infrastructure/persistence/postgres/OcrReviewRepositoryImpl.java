package com.notarist.review.infrastructure.persistence.postgres;

import com.notarist.review.application.port.out.OcrReviewRepository;
import com.notarist.review.domain.model.FieldAuditEntry;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.infrastructure.persistence.mapper.OcrReviewMapper;
import com.notarist.review.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * OCR-review persistence, tenant-isolated by PostgreSQL RLS.
 *
 * <p>The tenant identity is applied at the start of each method; {@code @Transactional} guarantees the
 * {@code notarist_set_identity()} call and the JPA work share one connection and transaction, which is
 * what makes the transaction-local setting visible to the row-level-security policy.
 *
 * <p><b>Save is deliberately split.</b> For a new review we persist a fresh root (version 0) and its
 * children cascade in. For an existing one we load the MANAGED root and copy the aggregate's mutable
 * state onto it and its managed children, so Hibernate's dirty checking and the {@code @Version}
 * column enforce optimistic locking — two concurrent reviewers cannot both win. New append-only audit
 * rows are inserted separately (never cascaded, never updated).
 */
@Repository
@Transactional
public class OcrReviewRepositoryImpl implements OcrReviewRepository {

    private final OcrReviewJpaRepository jpaRepository;
    private final OcrReviewFieldAuditJpaRepository auditJpaRepository;
    private final OcrReviewMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public OcrReviewRepositoryImpl(OcrReviewJpaRepository jpaRepository,
                                   OcrReviewFieldAuditJpaRepository auditJpaRepository,
                                   OcrReviewMapper mapper,
                                   RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.auditJpaRepository = auditJpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(OcrReview review) {
        rlsContextApplier.applyIfPresent(entityManager);
        String id = review.reviewId().value().toString();
        Optional<OcrReviewJpaEntity> existing = jpaRepository.findById(id);
        if (existing.isPresent()) {
            OcrReviewJpaEntity managed = existing.get();
            mapper.copyMutableState(review, managed);   // managed → dirty check + @Version bump
            jpaRepository.save(managed);
        } else {
            jpaRepository.save(mapper.toNewEntity(review));   // fresh root + cascaded children, version 0
        }
        // Append-only audit: only the entries recorded during this unit of work. Drained so a second
        // save in the same aggregate lifetime cannot re-insert them.
        List<FieldAuditEntry> pending = review.pullAuditEntries();
        for (FieldAuditEntry entry : pending) {
            auditJpaRepository.save(mapper.toAuditEntity(id, entry));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OcrReview> findByDocumentId(DocumentId documentId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByDocumentId(documentId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OcrReview> findById(ReviewId reviewId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(reviewId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDocumentId(DocumentId documentId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.existsByDocumentId(documentId.value().toString());
    }
}
