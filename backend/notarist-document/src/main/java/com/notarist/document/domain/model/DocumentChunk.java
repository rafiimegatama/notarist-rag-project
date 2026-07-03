package com.notarist.document.domain.model;

import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.domain.valueobject.DocumentId;

/** Domain entity for a processed text chunk. PII is redacted before storage. */
public class DocumentChunk {

    private final ChunkId chunkId;
    private final DocumentId documentId;
    private final int chunkIndex;
    private final String chunkText;
    private final int tokenCount;
    private final String chunkStrategy;
    private final Integer pageNumber;
    private final String sectionTitle;
    private final String pasalRef;

    public DocumentChunk(
            ChunkId chunkId,
            DocumentId documentId,
            int chunkIndex,
            String chunkText,
            int tokenCount,
            String chunkStrategy,
            Integer pageNumber,
            String sectionTitle,
            String pasalRef) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.tokenCount = tokenCount;
        this.chunkStrategy = chunkStrategy;
        this.pageNumber = pageNumber;
        this.sectionTitle = sectionTitle;
        this.pasalRef = pasalRef;
    }

    public ChunkId getChunkId() { return chunkId; }
    public DocumentId getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getChunkText() { return chunkText; }
    public int getTokenCount() { return tokenCount; }
    public String getChunkStrategy() { return chunkStrategy; }
    public Integer getPageNumber() { return pageNumber; }
    public String getSectionTitle() { return sectionTitle; }
    public String getPasalRef() { return pasalRef; }
}
