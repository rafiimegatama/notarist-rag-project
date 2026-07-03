package com.notarist.infra.qdrant;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.infra.resilience.DegradedModeRegistry;
import com.notarist.infra.resilience.NotaristRetryPolicy;
import com.notarist.search.application.port.out.VectorSearchPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Real Qdrant VectorSearchPort implementation.
 * Replaces Phase 3 QdrantSearchAdapter stub.
 *
 * Dependency: notarist-infra depends on notarist-search for VectorSearchPort interface.
 * Wire: Spring auto-wires this bean wherever VectorSearchPort is injected.
 *
 * Integration rules:
 *   - Timeout: QdrantProperties.searchTimeoutMs (5000ms default)
 *   - Retry: NotaristRetryPolicy (3 attempts, exponential backoff)
 *   - Filter: QdrantFilterBuilder (tenant + classification + docType + is_searchable)
 *   - Degradation: marks QDRANT degraded on failure; returns empty on degraded
 *   - Correlation ID: propagated in X-Correlation-Id header
 */
@Component
public class QdrantSearchAdapter implements VectorSearchPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantSearchAdapter.class);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate         qdrantRestTemplate;
    private final QdrantProperties     props;
    private final NotaristRetryPolicy  retryPolicy;
    private final DegradedModeRegistry degradedMode;
    private final Timer                searchTimer;

    public QdrantSearchAdapter(
            @Qualifier("qdrantRestTemplate") RestTemplate qdrantRestTemplate,
            QdrantProperties props,
            NotaristRetryPolicy retryPolicy,
            DegradedModeRegistry degradedMode,
            MeterRegistry meterRegistry) {
        this.qdrantRestTemplate = qdrantRestTemplate;
        this.props              = props;
        this.retryPolicy        = retryPolicy;
        this.degradedMode       = degradedMode;
        this.searchTimer        = Timer.builder("notarist.qdrant.search.duration")
                .description("Qdrant vector search latency")
                .register(meterRegistry);
    }

    @Override
    public List<VectorSearchResult> search(
            float[] queryVector,
            UUID tenantId,
            ClassificationLevel maxClassificationLevel,
            String documentTypeFilter,
            int limit,
            float minScore) {

        if (degradedMode.isDegraded(DegradedModeRegistry.ExternalService.QDRANT)) {
            log.warn("QdrantSearchAdapter: QDRANT is degraded — returning empty results");
            return List.of();
        }

        if (queryVector.length != QdrantVectorPayload.REQUIRED_DIMENSION) {
            log.error("QdrantSearchAdapter: invalid vector dimension {} (required {})",
                    queryVector.length, QdrantVectorPayload.REQUIRED_DIMENSION);
            return List.of();
        }

        return retryPolicy.execute("qdrant.search", () -> {
            long startMs = System.currentTimeMillis();
            try {
                Map<String, Object> body = buildSearchBody(
                        queryVector, tenantId, maxClassificationLevel, documentTypeFilter, limit, minScore);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                ResponseEntity<Map<String, Object>> response = qdrantRestTemplate.exchange(
                        props.collectionUrl() + "/points/search",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        RESPONSE_TYPE);

                long durationMs = System.currentTimeMillis() - startMs;
                searchTimer.record(durationMs, TimeUnit.MILLISECONDS);
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.QDRANT);

                List<VectorSearchResult> results = parseSearchResponse(response.getBody());
                log.debug("Qdrant search: tenantId={} results={} durationMs={}", tenantId, results.size(), durationMs);
                return results;

            } catch (ResourceAccessException e) {
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT, e.getMessage());
                log.error("Qdrant search timeout/unreachable: {}", e.getMessage());
                throw e;
            } catch (Exception e) {
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT, e.getMessage());
                log.error("Qdrant search failed: {}", e.getMessage(), e);
                throw e;
            }
        });
    }

    private Map<String, Object> buildSearchBody(
            float[] queryVector, UUID tenantId,
            ClassificationLevel maxLevel, String docType,
            int limit, float minScore) {

        // Convert float[] to List<Float> for Jackson serialization
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) vectorList.add(v);

        Map<String, Object> filter = QdrantFilterBuilder.must()
                .tenantId(tenantId)
                .maxClassification(maxLevel)
                .docType(docType)
                .onlySearchable()
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vectorList);
        body.put("limit", limit);
        body.put("score_threshold", minScore);
        body.put("filter", filter);
        body.put("with_payload", true);
        body.put("with_vector", false);
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<VectorSearchResult> parseSearchResponse(Map<String, Object> body) {
        if (body == null) return List.of();
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("result");
        if (results == null) return List.of();

        return results.stream().map(r -> {
            Map<String, Object> payload = (Map<String, Object>) r.get("payload");
            double score = ((Number) r.get("score")).doubleValue();
            return new VectorSearchResult(
                    str(payload, "chunk_id"),
                    str(payload, "document_id"),
                    str(payload, "tenant_id"),
                    str(payload, "doc_type"),
                    str(payload, "classification"),
                    intVal(payload, "chunk_index"),
                    str(payload, "section_title"),
                    (Integer) payload.get("page_number"),
                    str(payload, "chunk_text"),
                    str(payload, "source_object_key"),
                    score);
        }).collect(Collectors.toList());
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
