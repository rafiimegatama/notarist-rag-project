package com.notarist.search.application.pipeline;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalizes raw query: lowercasing, Indonesian stop-word removal,
 * and semantic alias expansion from the Notarist semantic dictionary.
 * No ML dependency — deterministic.
 */
@Component
public class QueryNormalizer {

    private static final Map<Pattern, String> ALIAS_EXPANSIONS = Map.of(
            Pattern.compile("(?i)\\bno akta\\b|\\bakta no\\b|\\bnomor akta\\b|\\bakta nomor\\b"), "nomor_akta",
            Pattern.compile("(?i)\\bnama client\\b|\\bnama klien\\b"),                             "client_name",
            Pattern.compile("(?i)\\bno sertifikat\\b"),                                             "certificate_number",
            Pattern.compile("(?i)\\bppat\\b"),                                                      "pejabat_pembuat_akta_tanah",
            Pattern.compile("(?i)\\bnotaris\\b"),                                                   "notary_name"
    );

    private static final Pattern STOP_WORDS = Pattern.compile(
            "(?i)\\b(yang|dan|atau|di|ke|dari|untuk|dengan|pada|oleh|ini|itu|ada|telah|akan|sudah)\\b"
    );

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalize(String rawQuery) {
        if (rawQuery == null) return "";
        String result = rawQuery.trim().toLowerCase();
        for (Map.Entry<Pattern, String> entry : ALIAS_EXPANSIONS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        result = STOP_WORDS.matcher(result).replaceAll(" ");
        result = WHITESPACE.matcher(result).replaceAll(" ").trim();
        return result;
    }

    public String[] tokenize(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) return new String[0];
        return normalizedQuery.split("\\s+");
    }
}
