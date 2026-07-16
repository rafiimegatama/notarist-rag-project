package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.LegalFactPort;
import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counting questions: "berapa akta bulan ini", "how many documents failed OCR".
 *
 * <p>Answers from SQL only. It holds no {@code RagPort}, so there is no code path from here to a
 * language model — the rule is enforced by the type system, not by discipline.
 */
@Component
@Order(10)
public class StatisticsStrategy implements AnswerStrategy {

    private final LegalFactPort facts;

    public StatisticsStrategy(LegalFactPort facts) {
        this.facts = facts;
    }

    @Override
    public String name() {
        return "StatisticsStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.STATISTICS;
    }

    @Override
    public boolean usesLlm() {
        return false;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();

        LegalFactPort.TimeWindow window = TimeWindows.parse(query.param(ClassifiedQuery.P_TIME_WINDOW));
        String jenisAkta = query.param(ClassifiedQuery.P_JENIS_AKTA);
        boolean ocrFailures = "OCR_FAILURE".equals(query.param(ClassifiedQuery.P_STATUS_TOPIC));

        LegalFactPort.CountFact fact = ocrFailures
                ? facts.countFailedDocuments(request.tenantId(), window)
                : facts.countDocuments(request.tenantId(), window, jenisAkta, null);

        long ms = System.currentTimeMillis() - start;

        if (fact.availability() == LegalFactPort.FactAvailability.NOT_IMPLEMENTED) {
            return AnswerResult.unsupported(fact.detail(), name(), ms);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", fact.count());
        data.put("timeWindow", window.name());
        if (jenisAkta != null) data.put("jenisAkta", jenisAkta);
        if (ocrFailures) data.put("filter", "INGESTION_FAILED");

        return AnswerResult.fromSql(render(fact.count(), window, jenisAkta, ocrFailures), data, name(), ms);
    }

    private String render(long count, LegalFactPort.TimeWindow window, String jenisAkta, boolean ocrFailures) {
        String subject = ocrFailures
                ? "dokumen yang gagal diproses"
                : (jenisAkta != null ? "akta " + jenisAkta : "dokumen");
        return String.format("Terdapat %d %s %s.", count, subject, TimeWindows.label(window));
    }
}
