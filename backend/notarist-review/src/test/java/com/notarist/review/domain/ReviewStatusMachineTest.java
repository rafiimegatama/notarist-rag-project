package com.notarist.review.domain;

import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.state.ReviewStatusMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The status transition table is the single source of truth for what is legal. */
class ReviewStatusMachineTest {

    @Test
    @DisplayName("only the linear forward edges are legal")
    void linearForwardEdgesLegal() {
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.PENDING, ReviewStatus.IN_PROGRESS)).isTrue();
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.IN_PROGRESS, ReviewStatus.REVIEW_COMPLETED)).isTrue();
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.REVIEW_COMPLETED, ReviewStatus.VERIFIED)).isTrue();
    }

    @Test
    @DisplayName("skipping a step is illegal")
    void skippingIsIllegal() {
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.PENDING, ReviewStatus.REVIEW_COMPLETED)).isFalse();
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.PENDING, ReviewStatus.VERIFIED)).isFalse();
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.IN_PROGRESS, ReviewStatus.VERIFIED)).isFalse();
    }

    @Test
    @DisplayName("going backwards is illegal")
    void backwardsIsIllegal() {
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.IN_PROGRESS, ReviewStatus.PENDING)).isFalse();
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.REVIEW_COMPLETED, ReviewStatus.IN_PROGRESS)).isFalse();
        assertThat(ReviewStatusMachine.isLegal(ReviewStatus.VERIFIED, ReviewStatus.REVIEW_COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("VERIFIED is terminal — no outbound edges")
    void verifiedIsTerminal() {
        assertThat(ReviewStatusMachine.allowedTargets(ReviewStatus.VERIFIED)).isEmpty();
        assertThat(ReviewStatus.VERIFIED.isTerminal()).isTrue();
    }
}
