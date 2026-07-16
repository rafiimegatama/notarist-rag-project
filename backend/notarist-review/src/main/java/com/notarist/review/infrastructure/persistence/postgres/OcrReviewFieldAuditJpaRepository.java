package com.notarist.review.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OcrReviewFieldAuditJpaRepository
        extends JpaRepository<OcrReviewFieldAuditJpaEntity, String> {

    List<OcrReviewFieldAuditJpaEntity> findByReviewIdOrderBySequenceAsc(String reviewId);
}
