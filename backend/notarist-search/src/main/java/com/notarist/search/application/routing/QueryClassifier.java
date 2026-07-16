package com.notarist.search.application.routing;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based classifier for the Indonesian legal-office domain.
 *
 * <p>Distinct from the existing {@code IntentClassifier}, which classifies a query in order to rank
 * <em>retrieval results</em>. This one classifies a query in order to decide <em>who is allowed to
 * answer it at all</em> — SQL or an LLM. That is a safety decision, so it is made by regex, not by a
 * model: a probabilistic router that mis-classifies "is deed 125 signed?" as a semantic question
 * would hand a legal-status question to an LLM, which is exactly the failure this sprint exists to
 * remove.
 *
 * <p>Ordering matters and is deliberate. Status and factual patterns are tested <em>before</em>
 * semantic ones, so a query that looks like both ("jelaskan status akta 125") is treated as the
 * safer of the two — a fact — rather than the more permissive one.
 */
@Component
public class QueryClassifier {

    // ---- FACTUAL: counting / statistics --------------------------------------------------------
    private static final List<Pattern> COUNTING = List.of(
            Pattern.compile("(?i)\\b(berapa|jumlah|total|banyaknya|hitung|count|how many)\\b"),
            Pattern.compile("(?i)\\b(rata.?rata|average|sum|statistik)\\b")
    );

    // ---- FACTUAL: grouped breakdown ------------------------------------------------------------
    private static final List<Pattern> GROUPING = List.of(
            Pattern.compile("(?i)\\b(per\\s+(jenis|tipe|status|bulan)|breakdown|kelompok|group by|masing.?masing)\\b"),
            Pattern.compile("(?i)\\b(rekap|rekapitulasi|ringkasan statistik)\\b")
    );

    // ---- FACTUAL: deadlines / reminders --------------------------------------------------------
    private static final List<Pattern> DEADLINE = List.of(
            Pattern.compile("(?i)\\b(jatuh tempo|kadaluarsa|kedaluwarsa|expire[sd]?|deadline|tenggat)\\b"),
            Pattern.compile("(?i)\\b(akan berakhir|segera berakhir|batas waktu|masa berlaku)\\b")
    );

    // ---- STATUS: lifecycle of a specific entity ------------------------------------------------
    private static final List<Pattern> STATUS = List.of(
            Pattern.compile("(?i)\\b(status|sudah|apakah sudah|belum|apakah telah|telah)\\b.*"
                    + "\\b(final|finalisasi|selesai|ditandatangani|tanda tangan|disetujui|approve[d]?|"
                    + "dikirim|delivered|diserahkan|terbit|diproses|diindeks)\\b"),
            Pattern.compile("(?i)\\b(siapa yang (menyetujui|approve|menandatangani|memverifikasi))\\b"),
            Pattern.compile("(?i)\\b(status (akta|dokumen|bundle|berkas|case|perkara))\\b"),
            Pattern.compile("(?i)\\b(is|has)\\b.*\\b(finalized|delivered|approved|signed)\\b")
    );

    // ---- STATUS: exact identifier lookup -------------------------------------------------------
    private static final Pattern NOMOR_AKTA = Pattern.compile(
            "(?i)\\b(?:akta\\s*(?:no\\.?|nomor)?\\s*|no\\.?\\s*|nomor\\s*)"
                    + "(\\d{1,5}(?:\\s*/\\s*[A-Za-z0-9.\\-]+){0,3})");

    // ---- SEMANTIC: similarity ------------------------------------------------------------------
    private static final List<Pattern> SIMILARITY = List.of(
            Pattern.compile("(?i)\\b(mirip|serupa|sejenis|similar|menyerupai|seperti)\\b"),
            Pattern.compile("(?i)\\b(cari\\s+(klausul|pasal|akta|dokumen)\\s+(yang\\s+)?(mirip|serupa))\\b"),
            Pattern.compile("(?i)\\b(contoh (klausul|akta|dokumen))\\b")
    );

    // ---- DOCUMENT INTELLIGENCE -----------------------------------------------------------------
    private static final List<Pattern> COMPARISON = List.of(
            Pattern.compile("(?i)\\b(bandingkan|perbandingan|compare|beda(nya)?|perbedaan|selisih)\\b")
    );

    private static final List<Pattern> SUMMARIZE = List.of(
            Pattern.compile("(?i)\\b(ringkas|ringkasan|rangkum|summarize|summary|intisari|poin utama)\\b")
    );

    private static final List<Pattern> EXPLAIN = List.of(
            Pattern.compile("(?i)\\b(jelaskan|penjelasan|explain|uraikan|maksud(nya)?|apa itu|apa yang dimaksud)\\b")
    );

    // ---- Parameter extraction ------------------------------------------------------------------
    private static final Pattern THIS_MONTH = Pattern.compile("(?i)\\b(bulan ini|this month|bulan berjalan)\\b");
    private static final Pattern TODAY      = Pattern.compile("(?i)\\b(hari ini|today|hari berjalan)\\b");
    private static final Pattern NEXT_WEEK  = Pattern.compile("(?i)\\b(minggu depan|pekan depan|next week|7 hari)\\b");
    private static final Pattern THIS_YEAR  = Pattern.compile("(?i)\\b(tahun ini|this year)\\b");

    private static final Pattern OCR_FAILURE = Pattern.compile(
            "(?i)\\b(gagal ocr|ocr gagal|failed ocr|ocr failed|gagal (diproses|proses|scan)|"
                    + "gagal (di)?index|pemrosesan gagal)\\b");

    /**
     * The user asked to SEE the documents, not to have them explained. Routes similarity queries to
     * retrieval-only (no LLM) instead of synthesis — a lawyer asking to see clauses wants the clause
     * text, not a paraphrase of it.
     */
    private static final Pattern WANTS_LIST = Pattern.compile(
            "(?i)\\b(tampilkan|daftar|list|tunjukkan|carikan|temukan|cari)\\b");

    private static final Map<String, Pattern> JENIS_AKTA = Map.of(
            "APHT",    Pattern.compile("(?i)\\bapht\\b"),
            "SKMHT",   Pattern.compile("(?i)\\bskmht\\b"),
            "FIDUSIA", Pattern.compile("(?i)\\bfidusia\\b"),
            "ROYA",    Pattern.compile("(?i)\\broya\\b"),
            "AJB",     Pattern.compile("(?i)\\bajb\\b"),
            "WASIAT",  Pattern.compile("(?i)\\bwasiat\\b"),
            "KUASA",   Pattern.compile("(?i)\\bkuasa\\b")
    );

    /**
     * Classifies a raw question.
     *
     * <p>Evaluation order is the safety property: deadline → counting/grouping → status →
     * identifier → comparison/summarize/explain → similarity → open question. Facts win ties.
     */
    public ClassifiedQuery classify(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        Map<String, String> params = extractParameters(q);

        // 1. Deadlines are factual and time-bounded — check before generic counting, because
        //    "berapa SKMHT jatuh tempo minggu depan" is both, and the deadline reading is narrower.
        if (matchesAny(q, DEADLINE)) {
            return new ClassifiedQuery(QuerySubtype.REMINDER, q, params);
        }

        // 2. Counting. A grouped count is an aggregation; a flat count is a statistic.
        if (matchesAny(q, COUNTING)) {
            QuerySubtype subtype = matchesAny(q, GROUPING) ? QuerySubtype.AGGREGATION : QuerySubtype.STATISTICS;
            return new ClassifiedQuery(subtype, q, params);
        }
        // A breakdown request without an explicit "berapa" is still an aggregation.
        if (matchesAny(q, GROUPING)) {
            return new ClassifiedQuery(QuerySubtype.AGGREGATION, q, params);
        }

        // 3. Legal / lifecycle status. Checked BEFORE the semantic patterns so that
        //    "jelaskan status akta 125" routes to SQL, not to the LLM.
        if (matchesAny(q, STATUS)) {
            return new ClassifiedQuery(QuerySubtype.STATUS_LOOKUP, q, params);
        }

        // 4. A bare identifier ("akta nomor 125/VII/2024") is a lookup, not a semantic question.
        if (params.containsKey(ClassifiedQuery.P_NOMOR_AKTA)) {
            return new ClassifiedQuery(QuerySubtype.IDENTIFIER_LOOKUP, q, params);
        }

        // 5. Document intelligence.
        if (matchesAny(q, COMPARISON)) {
            return new ClassifiedQuery(QuerySubtype.COMPARISON, q, params);
        }
        if (matchesAny(q, SUMMARIZE)) {
            return new ClassifiedQuery(QuerySubtype.SUMMARIZE, q, params);
        }

        // 6. Similarity before EXPLAIN: "jelaskan klausul yang mirip" is a retrieval request.
        if (matchesAny(q, SIMILARITY)) {
            return new ClassifiedQuery(QuerySubtype.SIMILARITY, q, params);
        }
        if (matchesAny(q, EXPLAIN)) {
            return new ClassifiedQuery(QuerySubtype.EXPLAIN, q, params);
        }

        // 7. Anything else — grounded, cited RAG.
        return new ClassifiedQuery(QuerySubtype.OPEN_QUESTION, q, params);
    }

    private Map<String, String> extractParameters(String q) {
        Map<String, String> params = new HashMap<>();

        Matcher nomor = NOMOR_AKTA.matcher(q);
        if (nomor.find()) {
            params.put(ClassifiedQuery.P_NOMOR_AKTA, nomor.group(1).replaceAll("\\s+", ""));
        }

        if (NEXT_WEEK.matcher(q).find())        params.put(ClassifiedQuery.P_TIME_WINDOW, "NEXT_WEEK");
        else if (TODAY.matcher(q).find())       params.put(ClassifiedQuery.P_TIME_WINDOW, "TODAY");
        else if (THIS_MONTH.matcher(q).find())  params.put(ClassifiedQuery.P_TIME_WINDOW, "THIS_MONTH");
        else if (THIS_YEAR.matcher(q).find())   params.put(ClassifiedQuery.P_TIME_WINDOW, "THIS_YEAR");

        if (OCR_FAILURE.matcher(q).find()) {
            params.put(ClassifiedQuery.P_STATUS_TOPIC, "OCR_FAILURE");
        }

        if (WANTS_LIST.matcher(q).find()) {
            params.put(ClassifiedQuery.P_WANTS_LIST, "true");
        }

        JENIS_AKTA.forEach((name, pattern) -> {
            if (pattern.matcher(q).find()) params.put(ClassifiedQuery.P_JENIS_AKTA, name);
        });

        if (Pattern.compile("(?i)\\bper\\s+jenis\\b").matcher(q).find()) {
            params.put(ClassifiedQuery.P_GROUP_BY, "JENIS_AKTA");
        } else if (Pattern.compile("(?i)\\bper\\s+status\\b").matcher(q).find()) {
            params.put(ClassifiedQuery.P_GROUP_BY, "STATUS");
        }

        return params;
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }
}
