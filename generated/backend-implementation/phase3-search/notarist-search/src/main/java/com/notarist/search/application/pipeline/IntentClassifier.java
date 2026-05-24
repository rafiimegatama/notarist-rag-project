package com.notarist.search.application.pipeline;

import com.notarist.search.application.query.SearchQuery;
import com.notarist.search.domain.model.SearchIntent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule-based intent classifier for Indonesian legal document domain.
 * No ML model — deterministic pattern matching.
 */
@Component
public class IntentClassifier {

    private static final List<Pattern> DOCUMENT_LOOKUP = List.of(
            Pattern.compile("(?i)\\b(nomor akta|no akta|akta no|akta nomor|no\\.?\\s*\\d+)\\b"),
            Pattern.compile("(?i)\\b(sertifikat|no sertif|cari akta|temukan akta)\\b")
    );

    private static final List<Pattern> REGULATION_LOOKUP = List.of(
            Pattern.compile("(?i)\\b(undang.?undang|peraturan pemerintah|pp no|kepmen|permen|perda|regulasi)\\b"),
            Pattern.compile("(?i)\\b(uu no|permenkeu|peraturan menteri|peraturan otoritas)\\b")
    );

    private static final List<Pattern> CITATION_LOOKUP = List.of(
            Pattern.compile("(?i)\\b(pasal|ayat|huruf|butir|angka|dasar hukum)\\b"),
            Pattern.compile("(?i)\\b(rujukan|referensi|dikutip|disebutkan dalam|berdasarkan)\\b")
    );

    private static final List<Pattern> RELATED_DOCUMENT = List.of(
            Pattern.compile("(?i)\\b(berkaitan|berhubungan|terkait|sejenis|dokumen lain)\\b"),
            Pattern.compile("(?i)\\b(yang sama|jenis yang|similar|dokumen serupa)\\b")
    );

    public SearchIntent classify(SearchQuery query) {
        if (query.intentOverride() != null) {
            return query.intentOverride();
        }
        String text = query.rawQuery();
        if (matchesAny(text, DOCUMENT_LOOKUP))  return SearchIntent.DOCUMENT_LOOKUP;
        if (matchesAny(text, REGULATION_LOOKUP)) return SearchIntent.REGULATION_LOOKUP;
        if (matchesAny(text, CITATION_LOOKUP))   return SearchIntent.CITATION_LOOKUP;
        if (matchesAny(text, RELATED_DOCUMENT))  return SearchIntent.RELATED_DOCUMENT;
        return SearchIntent.SEMANTIC_QUESTION;
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }
}
