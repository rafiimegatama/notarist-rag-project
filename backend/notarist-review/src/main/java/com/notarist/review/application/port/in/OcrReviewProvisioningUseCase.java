package com.notarist.review.application.port.in;

import com.notarist.review.api.response.OcrReviewResponse;
import com.notarist.review.application.command.InitializeReviewCommand;

/**
 * Provisions a review from completed OCR extraction. Not exposed over REST — OCR inference lives
 * outside this module. Kept as a use-case port so a future OCR-completion listener (or a test) can
 * seed a review without touching the aggregate directly.
 */
public interface OcrReviewProvisioningUseCase {

    OcrReviewResponse initializeReview(InitializeReviewCommand command);
}
