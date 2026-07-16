package com.notarist.search.application.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classifier is a safety component: it decides whether a question may be answered by a language
 * model. These tests pin the behaviour that matters — that facts are never classified as
 * LLM-eligible.
 */
class QueryClassifierTest {

    private final QueryClassifier classifier = new QueryClassifier();

    // ---- Category A: FACTUAL ---------------------------------------------------------------

    @ParameterizedTest
    @DisplayName("counting questions are FACTUAL and forbid the LLM")
    @ValueSource(strings = {
            "berapa akta bulan ini",
            "berapa jumlah dokumen hari ini",
            "total akta tahun ini",
            "how many deeds this month",
            "hitung dokumen fidusia"
    })
    void countingIsFactual(String query) {
        ClassifiedQuery result = classifier.classify(query);

        assertThat(result.category()).isEqualTo(QueryCategory.FACTUAL);
        assertThat(result.isLlmEligible())
                .as("a count has an exact answer; an LLM may only estimate it")
                .isFalse();
    }

    @Test
    @DisplayName("OCR failure counts are factual and carry the failure filter")
    void ocrFailureCount() {
        ClassifiedQuery result = classifier.classify("berapa dokumen yang gagal OCR");

        assertThat(result.subtype()).isEqualTo(QuerySubtype.STATISTICS);
        assertThat(result.param(ClassifiedQuery.P_STATUS_TOPIC)).isEqualTo("OCR_FAILURE");
        assertThat(result.isLlmEligible()).isFalse();
    }

    @Test
    @DisplayName("grouped counts route to AGGREGATION, not STATISTICS")
    void groupedCountIsAggregation() {
        ClassifiedQuery result = classifier.classify("berapa akta per jenis bulan ini");

        assertThat(result.subtype()).isEqualTo(QuerySubtype.AGGREGATION);
        assertThat(result.param(ClassifiedQuery.P_GROUP_BY)).isEqualTo("JENIS_AKTA");
        assertThat(result.param(ClassifiedQuery.P_TIME_WINDOW)).isEqualTo("THIS_MONTH");
        assertThat(result.isLlmEligible()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("deadline questions are FACTUAL — a missed SKMHT deadline voids the security interest")
    @ValueSource(strings = {
            "SKMHT yang jatuh tempo minggu depan",
            "akta apa yang segera kedaluwarsa",
            "which SKMHT expire next week",
            "dokumen dengan batas waktu minggu depan"
    })
    void deadlinesAreFactual(String query) {
        ClassifiedQuery result = classifier.classify(query);

        assertThat(result.subtype()).isEqualTo(QuerySubtype.REMINDER);
        assertThat(result.category()).isEqualTo(QueryCategory.FACTUAL);
        assertThat(result.isLlmEligible()).isFalse();
    }

    // ---- Category B: STATUS ----------------------------------------------------------------

    @ParameterizedTest
    @DisplayName("legal status questions are STATUS and forbid the LLM")
    @ValueSource(strings = {
            "apakah akta 125 sudah final",
            "apakah bundle ABC sudah dikirim",
            "siapa yang menyetujui bundle ini",
            "status akta nomor 125/VII/2024",
            "has deed 125 been finalized",
            "akta 99 sudah ditandatangani belum"
    })
    void statusIsNeverLlm(String query) {
        ClassifiedQuery result = classifier.classify(query);

        assertThat(result.category()).isEqualTo(QueryCategory.STATUS);
        assertThat(result.isLlmEligible())
                .as("an LLM inferring a legal status from document text is a liability")
                .isFalse();
    }

    @Test
    @DisplayName("a bare identifier is a lookup, not a semantic question")
    void identifierLookup() {
        ClassifiedQuery result = classifier.classify("akta nomor 125/VII/2024");

        assertThat(result.subtype()).isEqualTo(QuerySubtype.IDENTIFIER_LOOKUP);
        assertThat(result.param(ClassifiedQuery.P_NOMOR_AKTA)).isEqualTo("125/VII/2024");
        assertThat(result.isLlmEligible()).isFalse();
    }

    @Test
    @DisplayName("a status question mixing an explanatory verb still routes to SQL — facts win ties")
    void explanatoryVerbDoesNotDefeatStatus() {
        // "jelaskan" would otherwise classify as EXPLAIN (LLM-eligible). The status pattern must win,
        // otherwise a legal-status question quietly reaches a language model.
        ClassifiedQuery result = classifier.classify("jelaskan status akta 125 apakah sudah final");

        assertThat(result.category()).isEqualTo(QueryCategory.STATUS);
        assertThat(result.isLlmEligible()).isFalse();
    }

    @Test
    @DisplayName("a numeric difference is arithmetic, not comparison — stays factual")
    void numericDifferenceIsNotComparison() {
        ClassifiedQuery result = classifier.classify("berapa selisih jumlah akta bulan ini");

        assertThat(result.category()).isEqualTo(QueryCategory.FACTUAL);
        assertThat(result.isLlmEligible()).isFalse();
    }

    // ---- Category C: SEMANTIC --------------------------------------------------------------

    @Test
    @DisplayName("similarity with an explicit 'show me' routes to retrieval-only (no LLM)")
    void similarityListingWantsDocuments() {
        ClassifiedQuery result = classifier.classify("tampilkan klausul indemnity yang mirip");

        assertThat(result.subtype()).isEqualTo(QuerySubtype.SIMILARITY);
        assertThat(result.param(ClassifiedQuery.P_WANTS_LIST)).isEqualTo("true");
    }

    @Test
    @DisplayName("similarity without a listing verb is LLM-eligible (hybrid + synthesis)")
    void similaritySynthesis() {
        ClassifiedQuery result = classifier.classify("klausul indemnity yang mirip dengan ini");

        assertThat(result.subtype()).isEqualTo(QuerySubtype.SIMILARITY);
        assertThat(result.category()).isEqualTo(QueryCategory.SEMANTIC);
        assertThat(result.isLlmEligible()).isTrue();
        assertThat(result.parameters()).doesNotContainKey(ClassifiedQuery.P_WANTS_LIST);
    }

    // ---- Category D: DOCUMENT INTELLIGENCE --------------------------------------------------

    @ParameterizedTest
    @DisplayName("summarize / explain / compare are document intelligence — LLM allowed")
    @CsvSource({
            "ringkas isi dokumen ini,          SUMMARIZE",
            "jelaskan klausul ini,             EXPLAIN",
            "bandingkan dua dokumen ini,       COMPARISON",
            "apa perbedaan dengan perjanjian sebelumnya, COMPARISON"
    })
    void documentIntelligence(String query, QuerySubtype expected) {
        ClassifiedQuery result = classifier.classify(query);

        assertThat(result.subtype()).isEqualTo(expected);
        assertThat(result.category()).isEqualTo(QueryCategory.DOCUMENT_INTELLIGENCE);
        assertThat(result.isLlmEligible()).isTrue();
    }

    @Test
    @DisplayName("an unrecognised question falls back to grounded RAG")
    void openQuestionFallsBackToRag() {
        ClassifiedQuery result = classifier.classify("apa syarat sahnya perjanjian kredit");

        assertThat(result.subtype()).isEqualTo(QuerySubtype.OPEN_QUESTION);
        assertThat(result.isLlmEligible()).isTrue();
    }

    // ---- Parameter extraction ---------------------------------------------------------------

    @Test
    @DisplayName("time windows and jenis akta are extracted deterministically, by regex")
    void parameterExtraction() {
        ClassifiedQuery result = classifier.classify("berapa akta APHT bulan ini");

        assertThat(result.param(ClassifiedQuery.P_TIME_WINDOW)).isEqualTo("THIS_MONTH");
        assertThat(result.param(ClassifiedQuery.P_JENIS_AKTA)).isEqualTo("APHT");
    }

    @Test
    @DisplayName("blank input does not blow up — it falls back to an open question")
    void blankQuery() {
        assertThat(classifier.classify("").subtype()).isEqualTo(QuerySubtype.OPEN_QUESTION);
        assertThat(classifier.classify(null).subtype()).isEqualTo(QuerySubtype.OPEN_QUESTION);
    }
}
