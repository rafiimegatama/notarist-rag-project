package com.notarist.search.application.strategy;

import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Deadline questions: "SKMHT yang jatuh tempo minggu depan", "akta apa yang segera kedaluwarsa".
 *
 * <p><b>These cannot be answered yet, and this strategy says so.</b> A statutory deadline lives on a
 * Case/Deadline aggregate, and those tables do not exist until a later sprint. There is no column in
 * {@code dokumen_legal} that records when an SKMHT expires.
 *
 * <p>The important part is what it does <em>instead</em> of answering: nothing. It does not fall back
 * to the LLM. A model asked "which SKMHT expire next week?" will read some retrieved deed text and
 * produce a confident list of dates — and an SKMHT whose deadline is missed voids the security
 * interest it was created to protect. A wrong answer here is materially worse than "I don't know
 * yet", because a wrong answer gets acted upon.
 *
 * <p>When the Case/Deadline schema lands, this becomes a SQL query against {@code deadline} and
 * {@code reminder}. The routing, the guard and the API contract do not change — only this method body.
 */
@Component
@Order(12)
public class ReminderStrategy implements AnswerStrategy {

    private static final String NOT_AVAILABLE =
            "Informasi jatuh tempo belum tersedia. Data tenggat waktu (deadline) dan pengingat "
                    + "dikelola pada modul Case yang belum diimplementasikan, sehingga sistem tidak dapat "
                    + "menjawab pertanyaan ini secara akurat. Jawaban tidak dibuat oleh AI untuk "
                    + "menghindari informasi hukum yang keliru.";

    @Override
    public String name() {
        return "ReminderStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.REMINDER;
    }

    @Override
    public boolean usesLlm() {
        return false;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();
        return AnswerResult.unsupported(NOT_AVAILABLE, name(), System.currentTimeMillis() - start);
    }
}
