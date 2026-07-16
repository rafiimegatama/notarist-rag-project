package com.notarist.verification.domain;

import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.state.VerificationStatusMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The status transition table is the single source of truth for what is legal. */
class VerificationStatusMachineTest {

    @Test
    @DisplayName("the forward edges are legal")
    void forwardEdgesLegal() {
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.PENDING, VerificationStatus.UNDER_VERIFICATION)).isTrue();
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.UNDER_VERIFICATION, VerificationStatus.VERIFIED)).isTrue();
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.UNDER_VERIFICATION, VerificationStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("returns from an outcome back to UNDER_VERIFICATION are legal and flagged as returns")
    void returnsLegal() {
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.VERIFIED, VerificationStatus.UNDER_VERIFICATION)).isTrue();
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.FAILED, VerificationStatus.UNDER_VERIFICATION)).isTrue();
        assertThat(VerificationStatusMachine.isReturn(VerificationStatus.VERIFIED, VerificationStatus.UNDER_VERIFICATION)).isTrue();
        assertThat(VerificationStatusMachine.isReturn(VerificationStatus.FAILED, VerificationStatus.UNDER_VERIFICATION)).isTrue();
    }

    @Test
    @DisplayName("skipping or jumping between outcomes is illegal")
    void jumpsIllegal() {
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.PENDING, VerificationStatus.VERIFIED)).isFalse();
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.PENDING, VerificationStatus.FAILED)).isFalse();
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.VERIFIED, VerificationStatus.FAILED)).isFalse();
        assertThat(VerificationStatusMachine.isLegal(VerificationStatus.UNDER_VERIFICATION, VerificationStatus.PENDING)).isFalse();
    }
}
