package com.notarist.review.application.port.out;

import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.ReviewId;

import java.util.Optional;

/**
 * Persistence port for the {@link OcrReview} aggregate. Tenant isolation is enforced by PostgreSQL
 * row-level security in the adapter, not here.
 */
public interface OcrReviewRepository {

    /** Inserts a new review, or updates an existing one (optimistic-locked) and appends its new audit rows. */
    void save(OcrReview review);

    Optional<OcrReview> findByDocumentId(DocumentId documentId);

    Optional<OcrReview> findById(ReviewId reviewId);

    boolean existsByDocumentId(DocumentId documentId);
}
