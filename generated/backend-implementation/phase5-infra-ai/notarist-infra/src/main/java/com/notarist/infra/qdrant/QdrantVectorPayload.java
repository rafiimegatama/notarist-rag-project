package com.notarist.infra.qdrant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standardized vector payload stored in every Qdrant point.
 *
 * IMMUTABLE CONTRACT — never change field names without a full collection migration.
 * All fields are used in filter queries; renaming breaks existing indices.
 *
 * Embedding discipline:
 *   embeddingDimension MUST be 1024. Any attempt to upsert with a different
 *   dimension is rejected by QdrantIndexAdapter before the HTTP call.
 *   embeddingChecksum is the SHA-256 hex of the raw float[] bytes for audit.
 *
 * isSearchable = false when:
 *   - OCR confidence is below review threshold (LOW_CONFIDENCE_REVIEW)
 *   - Chunk is pending human review
 *   - Document is in DRAFT status
 * QdrantSearchAdapter always filters isSearchable = true.
 */
public record QdrantVectorPayload(
        @JsonProperty("document_id")        String documentId,
        @JsonProperty("chunk_id")           String chunkId,
        @JsonProperty("tenant_id")          String tenantId,
        @JsonProperty("classification")     String classification,
        @JsonProperty("classification_ordinal") int classificationOrdinal,
        @JsonProperty("doc_type")           String docType,
        @JsonProperty("regulation_id")      String regulationId,
        @JsonProperty("pasal_reference")    String pasalReference,
        @JsonProperty("embedding_model")    String embeddingModel,
        @JsonProperty("embedding_version")  String embeddingVersion,
        @JsonProperty("embedding_dimension") int embeddingDimension,
        @JsonProperty("embedding_checksum") String embeddingChecksum,
        @JsonProperty("is_searchable")      boolean isSearchable,
        @JsonProperty("source_object_key")  String sourceObjectKey,
        @JsonProperty("section_title")      String sectionTitle,
        @JsonProperty("chunk_index")        int chunkIndex,
        @JsonProperty("chunk_text")         String chunkText
) {
    public static final int REQUIRED_DIMENSION = 1024;
    public static final String EMBEDDING_MODEL = "bge-m3";
    public static final String EMBEDDING_VERSION = "1.0.0";

    public QdrantVectorPayload {
        if (embeddingDimension != REQUIRED_DIMENSION) {
            throw new IllegalArgumentException(
                    "Embedding dimension must be " + REQUIRED_DIMENSION + ", got: " + embeddingDimension);
        }
    }
}
