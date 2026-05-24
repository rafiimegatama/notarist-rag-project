package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.domain.model.FollowUpSuggestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based follow-up question generator for Indonesian notary/PPAT legal domain.
 *
 * Generates up to 3 relevant follow-up suggestions based on keywords detected
 * in the user query and assistant response. Real ML-based suggestion deferred to Phase 5.
 */
@Service
public class FollowUpSuggestionService {

    private static final int MAX_SUGGESTIONS = 3;

    public List<FollowUpSuggestion> suggest(String userQuery, String responseText) {
        String combined = (userQuery + " " + responseText).toLowerCase(Locale.ROOT);
        List<FollowUpSuggestion> suggestions = new ArrayList<>();

        if (combined.contains("akta") && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Apa saja persyaratan dokumen untuk membuat akta ini?",
                    "DOCUMENT_REQUIREMENTS", 1));
        }
        if ((combined.contains("sertifikat") || combined.contains("sertipikat")) && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Bagaimana prosedur balik nama sertifikat di kantor pertanahan?",
                    "CERTIFICATE_TRANSFER", 2));
        }
        if (combined.contains("fidusia") && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Apa kewajiban pelaporan fidusia ke Kementerian Hukum dan HAM?",
                    "FIDUSIA_REPORTING", 1));
        }
        if ((combined.contains("ppat") || combined.contains("pejabat pembuat akta tanah")) && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Apa batas kewenangan wilayah PPAT dalam membuat akta?",
                    "PPAT_JURISDICTION", 2));
        }
        if ((combined.contains("apht") || combined.contains("hak tanggungan")) && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Bagaimana tata cara pendaftaran hak tanggungan di BPN?",
                    "APHT_REGISTRATION", 1));
        }
        if ((combined.contains("roya") || combined.contains("hapus")) && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Dokumen apa yang diperlukan untuk mengajukan roya hak tanggungan?",
                    "ROYA_DOCUMENTS", 2));
        }
        if ((combined.contains("skmht") || combined.contains("surat kuasa")) && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Berapa masa berlaku SKMHT dan kapan harus dikonversi ke APHT?",
                    "SKMHT_VALIDITY", 1));
        }
        if ((combined.contains("waarmerking") || combined.contains("legalisasi")) && suggestions.size() < MAX_SUGGESTIONS) {
            suggestions.add(new FollowUpSuggestion(
                    "Apa perbedaan legalisasi dan waarmerking dokumen oleh notaris?",
                    "LEGALISASI_VS_WAARMERKING", 3));
        }

        // Generic fallback if no domain keywords matched
        if (suggestions.isEmpty()) {
            suggestions.add(new FollowUpSuggestion(
                    "Dokumen hukum apa yang relevan untuk kasus ini?",
                    "GENERAL_DOCUMENTS", 3));
            suggestions.add(new FollowUpSuggestion(
                    "Apakah ada regulasi terbaru yang mengatur hal ini?",
                    "REGULATORY_UPDATE", 3));
        }

        return suggestions.subList(0, Math.min(MAX_SUGGESTIONS, suggestions.size()));
    }
}
