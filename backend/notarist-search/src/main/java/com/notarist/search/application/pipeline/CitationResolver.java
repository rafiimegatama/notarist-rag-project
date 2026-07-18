package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.CitationEntry;
import com.notarist.search.domain.model.RetrievalReason;
import com.notarist.search.domain.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves retrieved chunks into citation entries.
 * Citations are built BEFORE context assembly — citation-first discipline.
 */
@Service
public class CitationResolver {

    private static final int CITATION_EXCERPT_CHARS = 200;

    public List<CitationEntry> resolve(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(this::toCitation)
                .collect(Collectors.toList());
    }

    private CitationEntry toCitation(RetrievedChunk chunk) {
        RetrievalReason primaryReason = selectPrimary(chunk.retrievalReasons());
        String excerpt = buildExcerpt(chunk.text());
        double score = chunk.rerankScore() > 0 ? chunk.rerankScore() : chunk.fusedScore();
        return new CitationEntry(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.documentType(),
                primaryReason,
                score,
                excerpt,
                chunk.text(),
                chunk.sourceObjectKey(),
                chunk.chunkIndex());
    }

    private RetrievalReason selectPrimary(Set<RetrievalReason> reasons) {
        if (reasons.contains(RetrievalReason.RERANK_BOOST))   return RetrievalReason.RERANK_BOOST;
        if (reasons.contains(RetrievalReason.SEMANTIC_MATCH)) return RetrievalReason.SEMANTIC_MATCH;
        if (reasons.contains(RetrievalReason.LEGAL_REFERENCE)) return RetrievalReason.LEGAL_REFERENCE;
        if (reasons.contains(RetrievalReason.CITATION_CHAIN))  return RetrievalReason.CITATION_CHAIN;
        return reasons.stream().findFirst().orElse(RetrievalReason.KEYWORD_MATCH);
    }

    private String buildExcerpt(String text) {
        if (text == null) return "";
        return text.length() > CITATION_EXCERPT_CHARS
                ? text.substring(0, CITATION_EXCERPT_CHARS) + "…"
                : text;
    }
}
