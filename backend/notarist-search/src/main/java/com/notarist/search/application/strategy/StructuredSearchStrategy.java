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
 * Lifecycle/status questions and exact identifier lookups:
 * "apakah akta 125 sudah final", "status akta nomor 125/VII/2024", "siapa yang menyetujui bundle X".
 *
 * <p>Answers from a SQL lookup on {@code dokumen_legal}. Holds no {@code RagPort}.
 *
 * <p><b>An honesty boundary worth understanding.</b> What exists today is the <em>ingestion</em>
 * status of a document (UPLOADED → … → INDEXED / FAILED / DLQ). What a notary means by "is deed 125
 * finalized?" is the <em>legal</em> status — signed, approved, delivered — which lives on the Case
 * aggregate and does not exist yet. Reporting "INDEXED" as though it answered "finalized" would be a
 * lie dressed as a fact, so this strategy reports the pipeline status it actually has and states
 * plainly that legal finalization is not yet tracked. It does not hand the question to an LLM to
 * paper over the gap.
 */
@Component
@Order(13)
public class StructuredSearchStrategy implements AnswerStrategy {

    private static final String LEGAL_STATUS_CAVEAT =
            "Catatan: sistem baru melacak status pemrosesan dokumen (OCR/indeks). Status hukum "
                    + "(finalisasi, persetujuan notaris, penyerahan) dikelola modul Case yang belum "
                    + "diimplementasikan dan tidak dapat dijawab saat ini.";

    private static final String CASE_SCOPED_NOT_AVAILABLE =
            "Status bundle/case (persetujuan, finalisasi, penyerahan) belum tersedia: modul Case dan "
                    + "Approval belum diimplementasikan. Jawaban tidak dibuat oleh AI untuk menghindari "
                    + "pernyataan status hukum yang tidak berdasar.";

    private final LegalFactPort facts;

    public StructuredSearchStrategy(LegalFactPort facts) {
        this.facts = facts;
    }

    @Override
    public String name() {
        return "StructuredSearchStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.STATUS_LOOKUP
                || query.subtype() == QuerySubtype.IDENTIFIER_LOOKUP;
    }

    @Override
    public boolean usesLlm() {
        return false;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();

        String nomorAkta = query.param(ClassifiedQuery.P_NOMOR_AKTA);

        // A status question with no resolvable document identifier is almost always about a bundle,
        // case or approval — none of which exist yet. Say so; do not guess.
        if (nomorAkta == null) {
            return AnswerResult.unsupported(
                    CASE_SCOPED_NOT_AVAILABLE, name(), System.currentTimeMillis() - start);
        }

        List<LegalFactPort.DocumentFact> found = facts.findDocumentStatus(request.tenantId(), nomorAkta);
        long ms = System.currentTimeMillis() - start;

        if (found.isEmpty()) {
            // Empty is a real, correct answer — "no such deed". It is NOT a reason to ask a model.
            Map<String, Object> empty = Map.of("nomorAkta", nomorAkta, "found", 0);
            return AnswerResult.fromSql(
                    String.format("Tidak ditemukan dokumen dengan nomor akta '%s'.", nomorAkta),
                    empty, name(), ms);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nomorAkta", nomorAkta);
        data.put("found", found.size());
        data.put("documents", found.stream().map(this::toMap).collect(Collectors.toList()));

        return AnswerResult.fromSql(render(nomorAkta, found), data, name(), ms);
    }

    private Map<String, Object> toMap(LegalFactPort.DocumentFact d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documentId", d.documentId());
        m.put("documentTitle", d.documentTitle());
        m.put("nomorAkta", d.nomorAkta());
        m.put("jenisAkta", d.jenisAkta());
        m.put("status", d.status());
        m.put("createdAt", d.createdAt());
        m.put("indexedAt", d.indexedAt());
        return m;
    }

    private String render(String nomorAkta, List<LegalFactPort.DocumentFact> found) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Ditemukan %d dokumen dengan nomor akta '%s':%n", found.size(), nomorAkta));
        for (LegalFactPort.DocumentFact d : found) {
            sb.append(String.format("- %s (%s) — status pemrosesan: %s%s%n",
                    d.documentTitle(),
                    d.jenisAkta() != null ? d.jenisAkta() : d.documentType(),
                    d.status(),
                    d.indexedAt() != null ? ", terindeks " + d.indexedAt() : ""));
        }
        sb.append('\n').append(LEGAL_STATUS_CAVEAT);
        return sb.toString();
    }
}
