package com.notarist.infra.qdrant;

import com.notarist.infra.resilience.DegradedModeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies the Qdrant collection exists at startup and creates it when absent — the vector-store
 * counterpart of {@link com.notarist.infra.gcs.GcsBucketProvisioner}, same posture, same failure
 * policy.
 *
 * <p>Nothing in this repository created the collection: not the app, not Terraform (Qdrant Cloud is
 * provisioned outside it), not docker-compose — the image starts empty. A fresh Qdrant therefore
 * failed EVERY search with "qdrant.search failed after 3 attempts" until someone hand-PUT the
 * collection, which is exactly the manual step this removes.
 *
 * <p><b>Dimension is verified, not assumed.</b> An existing collection with the wrong vector size is
 * NOT recreated — dropping it would silently destroy every indexed chunk. It is reported and Qdrant
 * is marked degraded instead, because a 1024-d query against, say, a 768-d collection fails
 * per-search anyway and only an operator can decide whether to reindex.
 *
 * <p><b>Failure policy: loud, not fatal.</b> Qdrant being unreachable at boot marks it degraded so
 * {@code /actuator/health} reports DOWN immediately, rather than the first search failing later.
 * Auth, documents and case CRUD still boot — same trade-off {@code OCR_FAIL_FAST=false} makes.
 */
@Component
@Order(0)
public class QdrantCollectionProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionProvisioner.class);

    /** bge-m3 is normalised, so cosine is the metric the embeddings were trained for. */
    private static final String REQUIRED_DISTANCE = "Cosine";

    private final RestTemplate         restTemplate;
    private final QdrantProperties     props;
    private final DegradedModeRegistry degradedMode;

    public QdrantCollectionProvisioner(
            @Qualifier("qdrantRestTemplate") RestTemplate restTemplate,
            QdrantProperties props,
            DegradedModeRegistry degradedMode) {
        this.restTemplate = restTemplate;
        this.props        = props;
        this.degradedMode = degradedMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        String collection = props.collectionName();
        try {
            Map<String, Object> existing = fetchCollection();
            if (existing != null) {
                verifyVectorConfig(existing, collection);
                return;
            }
            createCollection(collection);

        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT, e.getMessage());
            log.error("Qdrant collection provisioning FAILED collection={} url={}: {} — search and "
                            + "indexing will not work until this is resolved.",
                    collection, props.baseUrl(), e.getMessage(), e);
        }
    }

    /** @return the collection's result object, or null when Qdrant reports it does not exist. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchCollection() {
        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(props.collectionUrl(), HttpMethod.GET, null, Map.class);
            Map<String, Object> body = response.getBody();
            return body == null ? null : (Map<String, Object>) body.get("result");
        } catch (HttpClientErrorException.NotFound e) {
            return null;   // the one case that means "create it", not "something is wrong"
        }
    }

    private void createCollection(String collection) {
        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", QdrantVectorPayload.REQUIRED_DIMENSION,
                        "distance", REQUIRED_DISTANCE));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(props.collectionUrl(), HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);

        degradedMode.markHealthy(DegradedModeRegistry.ExternalService.QDRANT);
        log.info("Qdrant collection created: {} (size={} distance={})",
                collection, QdrantVectorPayload.REQUIRED_DIMENSION, REQUIRED_DISTANCE);
    }

    /**
     * An unnamed (default) vector is what {@link QdrantSearchAdapter} and {@link QdrantIndexAdapter}
     * both speak: they send {@code "vector": [...]} with no vector name. Qdrant reports that as a
     * flat {@code params.vectors} object; a named-vector collection reports a map of names instead,
     * which those adapters cannot query at all.
     */
    @SuppressWarnings("unchecked")
    private void verifyVectorConfig(Map<String, Object> result, String collection) {
        Map<String, Object> params  = (Map<String, Object>) result.get("config") == null
                ? null
                : (Map<String, Object>) ((Map<String, Object>) result.get("config")).get("params");
        Object vectors = params == null ? null : params.get("vectors");

        if (!(vectors instanceof Map<?, ?> vectorConfig) || !vectorConfig.containsKey("size")) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT,
                    "collection " + collection + " does not use a single unnamed vector");
            log.error("Qdrant collection {} is not shaped as a single unnamed vector (got: {}). "
                            + "QdrantSearchAdapter/QdrantIndexAdapter send an unnamed vector and "
                            + "cannot query this collection.", collection, vectors);
            return;
        }

        int size = ((Number) vectorConfig.get("size")).intValue();
        String distance = String.valueOf(vectorConfig.get("distance"));

        if (size != QdrantVectorPayload.REQUIRED_DIMENSION) {
            // Deliberately NOT recreated: that would drop every indexed chunk.
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT,
                    "collection " + collection + " has vector size " + size);
            log.error("Qdrant collection {} has vector size {}, expected {}. NOT recreating — that "
                            + "would delete every indexed chunk. Reindex into a correctly-sized "
                            + "collection, or point QDRANT_COLLECTION at one.",
                    collection, size, QdrantVectorPayload.REQUIRED_DIMENSION);
            return;
        }

        if (!REQUIRED_DISTANCE.equalsIgnoreCase(distance)) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT,
                    "collection " + collection + " uses distance " + distance);
            log.error("Qdrant collection {} uses distance {}, expected {}. Scores will not match the "
                            + "score_threshold the retrieval pipeline applies.",
                    collection, distance, REQUIRED_DISTANCE);
            return;
        }

        degradedMode.markHealthy(DegradedModeRegistry.ExternalService.QDRANT);
        log.info("Qdrant collection present: {} (size={} distance={})", collection, size, distance);
    }
}
