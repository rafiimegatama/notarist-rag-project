package com.notarist.review.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OcrReviewJpaRepository extends JpaRepository<OcrReviewJpaEntity, String> {

    Optional<OcrReviewJpaEntity> findByDocumentId(String documentId);

    boolean existsByDocumentId(String documentId);
}
