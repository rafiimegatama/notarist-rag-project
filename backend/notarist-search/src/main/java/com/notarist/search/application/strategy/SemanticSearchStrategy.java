package com.notarist.search.application.strategy;

import com.notarist.search.api.response.CitationResponse;
import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.application.port.in.SearchUseCase;
import com.notarist.search.application.query.SearchQuery;
import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Similarity retrieval where the user explicitly asked to <em>see the documents</em>:
 * "tampilkan klausul indemnity yang mirip", "daftar APHT serupa".
 *
 * <p>Runs the existing hybrid pipeline (BM25 + vector → RRF fusion → diversity → rerank) and returns
 * a <em>ranked list of real documents</em> with citations. It does <b>not</b> synthesise prose, and
 * holds no {@code RagPort}.
 *
 * <p>The distinction from {@link HybridSearchStrategy} is deliberate. When a lawyer says "show me
 * similar indemnity clauses", the honest answer is the clauses themselves — they want to read the
 * actual text, not a model's paraphrase of what it thinks the text says. Paraphrasing a contractual
 * clause is a good way to lose the word that mattered. Only when the user asks the system to
 * *explain* or *compare* does synthesis earn its place.
 */
@Component
@Order(19)
public class SemanticSearchStrategy implements AnswerStrategy {

    private final SearchUseCase searchUseCase;

    public SemanticSearchStrategy(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @Override
    public String name() {
        return "SemanticSearchStrategy";
    }

    /** Similarity queries that explicitly request a list of documents rather than an explanation. */
    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.SIMILARITY
                && Boolean.parseBoolean(query.paramOr(ClassifiedQuery.P_WANTS_LIST, "false"));
    }

    @Override
    public boolean usesLlm() {
        return false;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();

        SearchResponse response = searchUseCase.search(new SearchQuery(
                UUID.randomUUID(),
                request.rawQuery(),
                request.tenantId(),
                request.userId(),
                request.maxClassificationLevel(),
                request.documentTypeFilter(),
                null,
                request.maxResults(),
                request.contextTokenBudget(),
                request.correlationId()));

        long ms = System.currentTimeMillis() - start;

        if (!"SUCCESS".equals(response.status())) {
            return AnswerResult.unsupported(
                    "Pencarian gagal: " + response.errorMessage(), name(), ms);
        }

        List<AnswerCitation> citations = response.citations().stream()
                .map(c -> toCitation(request, c))
                .collect(Collectors.toList());

        return AnswerResult.fromRetrieval(
                render(citations), citations, name(), ms, response.retrievedChunkCount());
    }

    static AnswerCitation toCitation(AnswerRequest request, CitationResponse c) {
        return new AnswerCitation(
                c.chunkId(),
                c.documentId().toString(),
                c.sourceType(),
                request.maxClassificationLevel().name(),
                null,
                c.chunkIndex(),
                c.citationText(),
                c.sourceObjectKey(),
                c.relevanceScore());
    }

    private String render(List<AnswerCitation> citations) {
        if (citations.isEmpty()) {
            return "Tidak ditemukan dokumen yang serupa.";
        }
        String body = citations.stream()
                .map(c -> String.format("- [%s] %s", c.documentType(), truncate(c.chunkText())))
                .collect(Collectors.joining("\n"));
        return String.format("Ditemukan %d bagian dokumen yang relevan:%n%s", citations.size(), body);
    }

    private String truncate(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 157) + "...";
    }
}
