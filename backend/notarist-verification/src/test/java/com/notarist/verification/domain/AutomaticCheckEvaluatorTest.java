package com.notarist.verification.domain;

import com.notarist.verification.domain.service.AutomaticCheckEvaluator;
import com.notarist.verification.domain.service.AutomaticCheckResult;
import com.notarist.verification.domain.service.VerificationFacts;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The automatic checks are pure functions over OCR-review output — deterministic, no LLM, no I/O. */
class AutomaticCheckEvaluatorTest {

    private final AutomaticCheckEvaluator evaluator = new AutomaticCheckEvaluator();

    @Test
    @DisplayName("matching authority passes; a mismatch fails; missing data needs a human")
    void authority() {
        assertThat(evaluator.authorityMismatch(VerificationFacts.builder()
                .authority("Direksi PT ABC", "direksi pt abc").build()).decision()).isEqualTo(Decision.PASS);
        assertThat(evaluator.authorityMismatch(VerificationFacts.builder()
                .authority("Direksi PT ABC", "Komisaris PT ABC").build()).decision()).isEqualTo(Decision.FAIL);
        assertThat(evaluator.authorityMismatch(VerificationFacts.builder().build())
                .decision()).isEqualTo(Decision.MANUAL_REQUIRED);
    }

    @Test
    @DisplayName("director sets are compared order-insensitively")
    void directors() {
        assertThat(evaluator.directorMismatch(VerificationFacts.builder()
                .directors(List.of("Budi", "Ani"), List.of("ani", "budi")).build()).decision())
                .isEqualTo(Decision.PASS);
        assertThat(evaluator.directorMismatch(VerificationFacts.builder()
                .directors(List.of("Budi"), List.of("Budi", "Citra")).build()).decision())
                .isEqualTo(Decision.FAIL);
    }

    @Test
    @DisplayName("NPWP/NIK validity is checked by digit length")
    void npwpNik() {
        assertThat(evaluator.npwpNikMismatch(VerificationFacts.builder()
                .npwp("09.254.294.3-407.000").nik("3174091205880003").build()).decision())
                .isEqualTo(Decision.PASS);
        assertThat(evaluator.npwpNikMismatch(VerificationFacts.builder()
                .nik("123").build()).decision()).isEqualTo(Decision.FAIL);
    }

    @Test
    @DisplayName("duplicate certificate numbers fail; a unique set passes")
    void duplicateCertificate() {
        assertThat(evaluator.duplicateCertificateNumber(VerificationFacts.builder()
                .certificateNumbers(List.of("HGB-1", "HGB-2", "hgb-1")).build()).decision())
                .isEqualTo(Decision.FAIL);
        assertThat(evaluator.duplicateCertificateNumber(VerificationFacts.builder()
                .certificateNumbers(List.of("HGB-1", "HGB-2")).build()).decision())
                .isEqualTo(Decision.PASS);
    }

    @Test
    @DisplayName("an expired SKMHT deadline fails; one inside the window needs a human; a far one passes; none is N/A")
    void skmht() {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        assertThat(evaluator.skmhtDeadline(VerificationFacts.builder()
                .skmhtDeadline(now.minus(1, ChronoUnit.DAYS)).evaluatedAt(now).build()).decision())
                .isEqualTo(Decision.FAIL);
        assertThat(evaluator.skmhtDeadline(VerificationFacts.builder()
                .skmhtDeadline(now.plus(3, ChronoUnit.DAYS)).evaluatedAt(now).build()).decision())
                .isEqualTo(Decision.MANUAL_REQUIRED);
        assertThat(evaluator.skmhtDeadline(VerificationFacts.builder()
                .skmhtDeadline(now.plus(60, ChronoUnit.DAYS)).evaluatedAt(now).build()).decision())
                .isEqualTo(Decision.PASS);
        assertThat(evaluator.skmhtDeadline(VerificationFacts.builder().evaluatedAt(now).build()).decision())
                .isEqualTo(Decision.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("document consistency fails when a required document is missing")
    void documentConsistency() {
        assertThat(evaluator.documentConsistency(VerificationFacts.builder()
                .requiredDocuments(List.of("KTP", "NPWP", "SERTIFIKAT"))
                .presentDocuments(List.of("KTP", "NPWP")).build()).decision()).isEqualTo(Decision.FAIL);
        assertThat(evaluator.documentConsistency(VerificationFacts.builder()
                .requiredDocuments(List.of("KTP")).presentDocuments(List.of("ktp")).build()).decision())
                .isEqualTo(Decision.PASS);
    }

    @Test
    @DisplayName("evaluateAll returns one result per check, covering the automatic categories")
    void evaluateAll() {
        List<AutomaticCheckResult> results = evaluator.evaluateAll(VerificationFacts.builder().build());
        assertThat(results).hasSize(7);
        assertThat(results).extracting(AutomaticCheckResult::category)
                .contains(ChecklistCategory.AUTHORITY, ChecklistCategory.DIRECTOR, ChecklistCategory.NPWP_NIK,
                        ChecklistCategory.CERTIFICATE, ChecklistCategory.SKMHT_DEADLINE,
                        ChecklistCategory.DOCUMENT_CONSISTENCY);
    }
}
