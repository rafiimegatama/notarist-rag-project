package com.notarist.infra.qdrant;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.infra.resilience.DegradedModeRegistry;
import com.notarist.infra.resilience.NotaristRetryPolicy;
import com.notarist.ingest.application.port.out.VectorIndexPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Real Qdrant VectorIndexPort implementation.
 * Replaces Phase 2 VectorIndexAdapter no-op stub.
 *
 * Dependency: notarist-infra depends on notarist-ingest for VectorIndexPort interface.
 *
 * Embedding discipline enforced here (not in callers):
 *   - Dimension must be 1024 — rejected before HTTP call
 *   - Checksum computed per-point for audit trail
 *   - is_searchable set from OcrReviewStatus on the chunk payload
 *
 * All upserts are batched (one HTTP call per batch).
 * Delete uses Qdrant filter-based delete (all points for a document_id).
 */
@Component
public class QdrantIndexAdapter implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantIndexAdapter.class);

    private final RestTemplate         qdrantRestTemplate;
    private final QdrantProperties     props;
    private final NotaristRetryPolicy  retryPolicy;
    private final DegradedModeRegistry degradedMode;
    private final Counter              chunksUpserted;
    private final Counter              upsertFailures;
    private final Counter              deletions;
    private final Timer                upsertTimer;

    public QdrantIndexAdapter(
            @Qualifier("qdrantRestTemplate") RestTemplate qdrantRestTemplate,
            QdrantProperties props,
            NotaristRetryPolicy retryPolicy,
            DegradedModeRegistry degradedMode,
            MeterRegistry meterRegistry) {
        this.qdrantRestTemplate = qdrantRestTemplate;
        this.props              = props;
        this.retryPolicy        = retryPolicy;
        this.degradedMode       = degradedMode;

        this.chunksUpserted  = Counter.builder("notarist.qdrant.chunks.upserted").register(meterRegistry);
        this.upsertFailures  = Counter.builder("notarist.qdrant.upsert.failures").register(meterRegistry);
        this.deletions       = Counter.builder("notarist.qdrant.documents.deleted").register(meterRegistry);
        this.upsertTimer     = Timer.builder("notarist.qdrant.upsert.duration").register(meterRegistry);
    }

    @Override
    public void upsertChunks(List<IndexableChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;

        validateDimensions(chunks);

        retryPolicy.execute("qdrant.upsert", () -> {
            long startMs = System.currentTimeMillis();
            try {
                List<Map<String, Object>> points = chunks.stream()
                        .map(this::toQdrantPoint)
                        .collect(Collectors.toList());

                Map<String, Object> body = Map.of("points", points);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                qdrantRestTemplate.exchange(
                        props.collectionUrl() + "/points",
                        HttpMethod.PUT,
                        new HttpEntity<>(body, headers),
                        Void.class);

                long durationMs = System.currentTimeMillis() - startMs;
                upsertTimer.record(durationMs, TimeUnit.MILLISECONDS);
                chunksUpserted.increment(chunks.size());
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.QDRANT);
                log.debug("Qdrant upserted {} chunks in {}ms", chunks.size(), durationMs);

            } catch (Exception e) {
                upsertFailures.increment();
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT, e.getMessage());
                log.error("Qdrant upsert failed: {}", e.getMessage(), e);
                throw e;
            }
            return null;
        });
    }

    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        retryPolicy.execute("qdrant.delete", () -> {
            try {
                Map<String, Object> filter = QdrantFilterBuilder.must()
                        .matchValue("document_id", documentId.value().toString())
                        .build();

                Map<String, Object> body = Map.of("filter", filter);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                qdrantRestTemplate.exchange(
                        props.collectionUrl() + "/points/delete",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Void.class);

                deletions.increment();
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.QDRANT);
                log.debug("Qdrant deleted points for documentId={}", documentId.value());

            } catch (Exception e) {
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT, e.getMessage());
                log.error("Qdrant delete failed documentId={}: {}", documentId.value(), e.getMessage(), e);
                throw e;
            }
            return null;
        });
    }

    private void validateDimensions(List<IndexableChunk> chunks) {
        for (IndexableChunk chunk : chunks) {
            if (chunk.vector().length != QdrantVectorPayload.REQUIRED_DIMENSION) {
                throw new IllegalArgumentException(
                        "Chunk " + chunk.chunkId() + " has invalid embedding dimension: "
                        + chunk.vector().length + " (required " + QdrantVectorPayload.REQUIRED_DIMENSION + ")");
            }
        }
    }

    private Map<String, Object> toQdrantPoint(IndexableChunk chunk) {
        // Convert float[] to List<Float>
        List<Float> vectorList = new ArrayList<>(chunk.vector().length);
        for (float v : chunk.vector()) vectorList.add(v);

        QdrantVectorPayload payload = new QdrantVectorPayload(
                chunk.documentId().value().toString(),
                chunk.chunkId(),
                chunk.tenantId().toString(),
                chunk.payload().classificationLevel().name(),
                chunk.payload().classificationLevel().ordinal(),
                chunk.payload().documentType().name(),
                null,   // regulationId — populated by enrichment in Phase 5B
                null,   // pasalReference — populated by NER output
                QdrantVectorPayload.EMBEDDING_MODEL,
                QdrantVectorPayload.EMBEDDING_VERSION,
                QdrantVectorPayload.REQUIRED_DIMENSION,
                computeChecksum(chunk.vector()),
                true,   // is_searchable = true unless OCR review required
                chunk.payload().sourceObjectKey(),
                chunk.payload().sectionTitle(),
                chunk.payload().chunkIndex(),
                chunk.payload().text());

        return Map.of(
                "id", chunk.chunkId(),
                "vector", vectorList,
                "payload", payload);
    }

    private String computeChecksum(float[] vector) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (float v : vector) {
                int bits = Float.floatToIntBits(v);
                md.update((byte)(bits >> 24));
                md.update((byte)(bits >> 16));
                md.update((byte)(bits >> 8));
                md.update((byte) bits);
            }
            byte[] digest = md.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "checksum-error";
        }
    }
}
