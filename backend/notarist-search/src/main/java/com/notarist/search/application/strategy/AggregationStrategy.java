package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.LegalFactPort;
import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Grouped breakdowns: "berapa akta per jenis", "rekap dokumen per status".
 *
 * <p>SQL {@code GROUP BY}. An LLM asked to produce a breakdown from retrieved chunks will produce a
 * plausible-looking table whose numbers do not add up — which is worse than no table, because it
 * looks authoritative. Holds no {@code RagPort}.
 */
@Component
@Order(11)
public class AggregationStrategy implements AnswerStrategy {

    private final LegalFactPort facts;

    public AggregationStrategy(LegalFactPort facts) {
        this.facts = facts;
    }

    @Override
    public String name() {
        return "AggregationStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.AGGREGATION;
    }

    @Override
    public boolean usesLlm() {
        return false;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();

        LegalFactPort.TimeWindow window = TimeWindows.parse(query.param(ClassifiedQuery.P_TIME_WINDOW));
        LegalFactPort.GroupBy groupBy = parseGroupBy(query.param(ClassifiedQuery.P_GROUP_BY));

        List<LegalFactPort.GroupCount> rows = facts.countGrouped(request.tenantId(), window, groupBy);
        long ms = System.currentTimeMillis() - start;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupBy", groupBy.name());
        data.put("timeWindow", window.name());
        data.put("rows", rows.stream()
                .map(r -> Map.of("key", r.key(), "count", r.count()))
                .collect(Collectors.toList()));
        data.put("total", rows.stream().mapToLong(LegalFactPort.GroupCount::count).sum());

        return AnswerResult.fromSql(render(rows, groupBy, window), data, name(), ms);
    }

    private LegalFactPort.GroupBy parseGroupBy(String raw) {
        if (raw == null) return LegalFactPort.GroupBy.JENIS_AKTA;
        try {
            return LegalFactPort.GroupBy.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return LegalFactPort.GroupBy.JENIS_AKTA;
        }
    }

    private String render(List<LegalFactPort.GroupCount> rows,
                          LegalFactPort.GroupBy groupBy,
                          LegalFactPort.TimeWindow window) {
        if (rows.isEmpty()) {
            return String.format("Tidak ada data %s untuk dikelompokkan.", TimeWindows.label(window));
        }
        String body = rows.stream()
                .map(r -> String.format("- %s: %d", r.key(), r.count()))
                .collect(Collectors.joining("\n"));
        return String.format("Rekapitulasi per %s (%s):%n%s",
                groupBy.name().toLowerCase().replace('_', ' '), TimeWindows.label(window), body);
    }
}
