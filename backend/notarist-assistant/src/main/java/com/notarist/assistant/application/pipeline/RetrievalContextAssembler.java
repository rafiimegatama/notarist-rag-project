package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.application.port.out.SearchPort.RetrievedChunkDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Assembles retrieved chunks into a formatted context string for prompt injection.
 *
 * Format per chunk:
 *   [Sumber: {chunkId}]
 *   Dokumen: {documentType} | {sectionTitle} | Halaman: {pageNumber}
 *   {chunkText}
 *   ---
 *
 * The [Sumber: X] prefix aligns with the prompt instruction so the LLM
 * can produce traceable citation references in its response.
 */
@Service
public class RetrievalContextAssembler {

    public String assemble(List<RetrievedChunkDto> chunks, List<CitationDto> citations) {
        if (chunks.isEmpty()) {
            return "(Tidak ada dokumen yang relevan ditemukan untuk pertanyaan ini.)";
        }

        StringBuilder sb = new StringBuilder();
        for (RetrievedChunkDto chunk : chunks) {
            sb.append("[Sumber: ").append(chunk.chunkId()).append("]\n");
            sb.append("Dokumen: ").append(chunk.documentType());
            if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
                sb.append(" | ").append(chunk.sectionTitle());
            }
            if (chunk.pageNumber() != null) {
                sb.append(" | Halaman: ").append(chunk.pageNumber());
            }
            sb.append('\n');
            sb.append(chunk.chunkText());
            sb.append("\n---\n");
        }
        return sb.toString().trim();
    }
}
