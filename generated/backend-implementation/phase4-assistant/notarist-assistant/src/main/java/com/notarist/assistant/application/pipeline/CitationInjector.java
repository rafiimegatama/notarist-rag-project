package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.application.port.out.SearchPort.RetrievedChunkDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds citation entries from retrieved chunks BEFORE the LLM is called.
 *
 * Citations are constructed from retrieval metadata — not from the LLM response.
 * The injected list is embedded in the prompt and attached to the response
 * so HallucinationGuard can cross-reference.
 */
@Service
public class CitationInjector {

    /**
     * Converts retrieved chunks to CitationDto records.
     * Called BEFORE PromptBuilder so citations are available for prompt injection.
     */
    public List<CitationDto> buildCitations(List<RetrievedChunkDto> chunks) {
        return chunks.stream()
                .map(this::toCitationDto)
                .collect(Collectors.toList());
    }

    /**
     * Formats citation references as a text block for inclusion in the prompt.
     * Format: [Sumber: {chunkId}] {sectionTitle} — {sourceObjectKey}
     */
    public String formatCitationBlock(List<CitationDto> citations) {
        if (citations.isEmpty()) return "(Tidak ada sumber dokumen tersedia)";

        StringBuilder sb = new StringBuilder();
        for (CitationDto c : citations) {
            sb.append("[Sumber: ").append(c.chunkId()).append("] ");
            if (c.sectionTitle() != null && !c.sectionTitle().isBlank()) {
                sb.append(c.sectionTitle()).append(" — ");
            }
            sb.append(c.sourceObjectKey()).append('\n');
        }
        return sb.toString();
    }

    private CitationDto toCitationDto(RetrievedChunkDto chunk) {
        return new CitationDto(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.documentType(),
                chunk.classificationLevel(),
                chunk.sectionTitle(),
                chunk.chunkIndex(),
                chunk.chunkText(),
                chunk.sourceObjectKey(),
                chunk.relevanceScore());
    }
}
