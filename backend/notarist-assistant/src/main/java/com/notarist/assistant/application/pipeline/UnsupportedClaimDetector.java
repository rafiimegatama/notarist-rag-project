package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.api.response.CitationDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects strong assertions in LLM response text that lack a citation marker.
 *
 * Strategy (Phase 4 — rule-based stub):
 *   - Splits response into sentences.
 *   - Flags sentences containing absolutist language patterns (Indonesian legal domain)
 *     that do NOT also contain a [Sumber: X] citation marker.
 *   - Real NLP-based cross-encoder claim verification deferred to Phase 5.
 *
 * Absolutist patterns:
 *   selalu, tidak pernah, semua, setiap, wajib, dilarang, harus, pasti, tidak boleh
 */
@Service
public class UnsupportedClaimDetector {

    private static final Pattern CITATION_MARKER  = Pattern.compile("\\[Sumber:\\s*[^\\]]+\\]");
    private static final Pattern ABSOLUTIST_TERMS = Pattern.compile(
            "\\b(selalu|tidak pernah|semua|setiap|wajib|dilarang|harus|pasti|tidak boleh)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Returns a list of unsupported claim descriptions (human-readable).
     * Empty list means no unsupported claims detected.
     */
    public List<String> detect(String responseText, List<CitationDto> citations) {
        if (responseText == null || responseText.isBlank()) return List.of();

        List<String> unsupported = new ArrayList<>();
        String[] sentences = SENTENCE_SPLIT.split(responseText);

        for (String sentence : sentences) {
            if (ABSOLUTIST_TERMS.matcher(sentence).find()
                    && !CITATION_MARKER.matcher(sentence).find()) {
                String truncated = sentence.length() > 120
                        ? sentence.substring(0, 120) + "..." : sentence;
                unsupported.add("Klaim tanpa sumber: \"" + truncated + "\"");
            }
        }

        return List.copyOf(unsupported);
    }
}
