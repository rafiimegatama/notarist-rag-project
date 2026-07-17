package com.notarist.runtime.ocr.provider.paddle;

import com.notarist.runtime.ocr.config.OcrProperties;
import com.notarist.runtime.ocr.spi.OcrCapabilities;
import com.notarist.runtime.ocr.spi.OcrExtractionRequest;
import com.notarist.runtime.ocr.spi.OcrExtractionResult;
import com.notarist.runtime.ocr.spi.OcrProvider;
import com.notarist.runtime.ocr.spi.OcrProviderException;
import com.notarist.runtime.ocr.spi.OcrProviderHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PaddleOCR, behind the {@link OcrProvider} SPI.
 *
 * <p>This is the old {@code PaddleOcrAdapter}, with everything that was not PaddleOCR taken out of
 * it. It no longer implements {@code OcrServicePort} (the runtime does), no longer reads its
 * endpoint from the shared {@code ModelRegistry} (it owns its own config), no longer tags metrics
 * with the shared {@code ModelProvider} enum, and no longer contains a hardcoded 30-second timeout
 * or a degradation short-circuit. All of that moved up into {@code OcrRuntimeService}, once, for
 * every engine.
 *
 * <p>What is left is the only thing that is actually PaddleOCR-specific: its HTTP shape, its JSON
 * field names, and its confidence scale.
 *
 * <p>Deployment is irrelevant to this class. It speaks HTTP to a configured endpoint — a local
 * PaddleOCR serving process on a GPU box, a container on another host, a Cloud Run service. There is
 * no localhost default and no GPU assumption anywhere in it; the GPU shows up only as a
 * {@code batchSizeHint} the sidecar is free to ignore.
 */
@Component
public class PaddleOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrProvider.class);

    public static final String ID = "paddle";

    private static final String DEFAULT_PATH = "/predict/ocr_system";
    private static final String HEALTH_PATH = "/health";

    /**
     * PaddleOCR reports per-token confidence on a 0.0–1.0 scale already, so normalisation is the
     * identity. It is asserted rather than assumed: a future PaddleOCR serving build that switched
     * to 0–100 would otherwise silently push every document past the confidence threshold and
     * auto-accept scans that should have gone to human review.
     */
    private static final float MAX_RAW_CONFIDENCE = 1.0f;

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String extractUrl;
    private final String configurationError;

    public PaddleOcrProvider(
            @Qualifier("aiRuntimeRestTemplate") RestTemplate restTemplate,
            OcrProperties properties) {
        this.restTemplate = restTemplate;

        // Resolve the endpoint here, but do NOT throw if it is missing.
        //
        // The tempting move is to fail the constructor: no endpoint, no boot, very strict. It is
        // wrong. It converts "OCR is unconfigured" into "the application does not start", which also
        // takes down auth, document CRUD and search — every endpoint that has nothing to do with OCR.
        // A missing OCR sidecar should degrade OCR, not the product.
        //
        // So a missing endpoint is recorded and surfaced through the three places that can act on it:
        // the startup probe logs it, /actuator/health reports the provider DOWN with the reason, and
        // extract() throws a PERMANENT error naming the property. Operators who DO want a hard boot
        // failure opt in with notarist.ocr.fail-fast-on-unhealthy-provider=true.
        //
        // What is NOT done, in any branch: default to localhost. That is the failure this whole
        // design exists to avoid — it works on a laptop and dies silently in a container.
        String configured = properties.getProviders().containsKey(ID)
                ? properties.getProviders().get(ID).getEndpoint()
                : null;

        if (configured == null || configured.isBlank()) {
            this.endpoint = null;
            this.extractUrl = null;
            this.configurationError =
                    "PaddleOCR has no endpoint. Set notarist.ocr.providers.paddle.endpoint "
                    + "(env: OCR_BASE_URL). There is deliberately no localhost default.";
            log.error("PaddleOcrProvider: {} — OCR will fail until this is set.", configurationError);
        } else {
            OcrProperties.ProviderConfig config = properties.getProviders().get(ID);
            this.endpoint = stripTrailingSlash(configured);
            String path = (config.getPath() == null || config.getPath().isBlank())
                    ? DEFAULT_PATH
                    : config.getPath();
            this.extractUrl = this.endpoint + path;
            this.configurationError = null;
            log.info("PaddleOcrProvider: endpoint={} extractUrl={}", endpoint, extractUrl);
        }
    }

    /** Permanent, not retryable: no amount of backoff will conjure a configuration value. */
    private void requireConfigured() {
        if (configurationError != null) {
            throw OcrProviderException.permanent(ID, configurationError, null);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "PaddleOCR";
    }

    /**
     * PaddleOCR's declared capability matrix.
     *
     * <p>Every flag is what the engine ACTUALLY does, not what we wish it did. Handwriting is the one
     * that matters: PaddleOCR's stock detection/recognition models are trained on printed text and
     * are poor at cursive. Declaring {@code supportsHandwriting = true} to look good would make the
     * runtime skip its warning, and a handwritten margin note on an akta would go missing silently.
     * Declaring it false makes the runtime say so, out loud, on every request that asks for it.
     */
    @Override
    public OcrCapabilities capabilities() {
        return new OcrCapabilities(
                true,   // vision      — raster scans and photographs, its core case
                true,   // pdf         — accepts PDFs directly; no client-side rasterising
                false,  // handwriting — stock models are printed-text only. See above.
                true,   // tables      — via PP-Structure
                true,   // layout      — PP-Structure returns blocks and reading order
                true,   // batch       — the serving layer batches on the device
                true,   // gpu         — exploits CUDA when present. Does NOT require it.
                16,     // engine's own ceiling; OcrBatchSizer clamps the hardware value to this
                java.util.Set.of("id", "en", "ch"));
    }

    @Override
    public OcrExtractionResult extract(OcrExtractionRequest request) throws OcrProviderException {
        requireConfigured();
        long startMs = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        // The document is passed by reference; the sidecar fetches it from object storage itself.
        body.put("source_object_key", request.sourceObjectKey());
        body.put("language", request.language());
        body.put("enable_tables", request.enableTables());
        body.put("min_confidence", request.minConfidenceThreshold());
        // A HINT. The sidecar decides what its device can actually hold; we do not presume to know.
        body.put("batch_size", request.batchSizeHint());

        Map<String, Object> response = post(extractUrl, body, request.sourceObjectKey());

        return parse(response, request.sourceObjectKey(), System.currentTimeMillis() - startMs);
    }

    @Override
    public List<OcrExtractionResult> extractBatch(List<OcrExtractionRequest> requests)
            throws OcrProviderException {
        requireConfigured();
        if (requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() == 1) {
            return List.of(extract(requests.get(0)));
        }

        long startMs = System.currentTimeMillis();
        OcrExtractionRequest first = requests.get(0);

        Map<String, Object> body = new HashMap<>();
        body.put("source_object_keys", requests.stream().map(OcrExtractionRequest::sourceObjectKey).toList());
        body.put("language", first.language());
        body.put("enable_tables", first.enableTables());
        body.put("min_confidence", first.minConfidenceThreshold());
        body.put("batch_size", first.batchSizeHint());

        Map<String, Object> response = post(extractUrl + "/batch", body, "batch:" + requests.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("results");

        if (items == null || items.size() != requests.size()) {
            // Alignment is the whole contract of a batch call. Attaching one akta's OCR text to
            // another akta's record is not a bug we can allow to be quiet.
            throw OcrProviderException.permanent(ID,
                    "PaddleOCR batch returned " + (items == null ? "null" : items.size())
                    + " results for " + requests.size() + " requests", null);
        }

        long durationMs = System.currentTimeMillis() - startMs;
        List<OcrExtractionResult> results = new java.util.ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            results.add(parse(items.get(i), requests.get(i).sourceObjectKey(), durationMs));
        }
        return results;
    }

    @Override
    public OcrProviderHealth health() {
        if (configurationError != null) {
            // DOWN, with the fix in the message. This is what makes an unconfigured endpoint
            // diagnosable from /actuator/health instead of from a stack trace an hour later.
            return OcrProviderHealth.down(ID, configurationError);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint + HEALTH_PATH, HttpMethod.GET, null, String.class);

            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                return OcrProviderHealth.up(ID, "PaddleOCR reachable",
                        Map.of("endpoint", endpoint, "status", status.value()));
            }
            return OcrProviderHealth.down(ID,
                    "PaddleOCR returned HTTP " + status.value() + " from " + endpoint + HEALTH_PATH);

        } catch (Exception e) {
            // Contractually must not throw — the health endpoint has to be able to REPORT the outage.
            return OcrProviderHealth.down(ID,
                    "PaddleOCR unreachable at " + endpoint + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> body, String context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw OcrProviderException.retryable(ID,
                        "PaddleOCR returned an empty body for " + context, null);
            }
            return result;

        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, socket timeout — the engine is not there right now.
            throw OcrProviderException.retryable(ID,
                    "PaddleOCR unreachable at " + url + " for " + context + ": " + e.getMessage(), e);

        } catch (HttpServerErrorException e) {
            // 5xx: the engine is unwell (often: GPU OOM under load). Worth another attempt.
            throw OcrProviderException.retryable(ID,
                    "PaddleOCR " + e.getStatusCode() + " for " + context, e);

        } catch (HttpClientErrorException e) {
            // 4xx: WE sent something it will not accept — a corrupt PDF, an unsupported language.
            // Retrying sends the identical bytes and gets the identical 4xx, three times, slower.
            throw OcrProviderException.permanent(ID,
                    "PaddleOCR rejected the request (" + e.getStatusCode() + ") for " + context
                    + ": " + e.getResponseBodyAsString(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private OcrExtractionResult parse(Map<String, Object> body, String sourceObjectKey, long durationMs) {
        String ocrObjectKey = (String) body.getOrDefault("ocr_object_key", sourceObjectKey + ".ocr.txt");
        int pageCount = toInt(body.get("page_count"));
        int textLength = toInt(body.get("text_length"));
        float rawConfidence = toFloat(body.get("confidence_avg"));
        List<String> warnings = (List<String>) body.getOrDefault("warnings", List.of());

        long reported = toLong(body.get("duration_ms"));
        long effectiveDuration = reported > 0 ? reported : durationMs;

        return new OcrExtractionResult(
                ID,
                ocrObjectKey,
                pageCount,
                textLength,
                normaliseConfidence(rawConfidence, sourceObjectKey),
                warnings,
                effectiveDuration);
    }

    /**
     * PaddleOCR already reports 0.0–1.0. If that ever stops being true, fail loudly here rather than
     * letting an out-of-range score flow into OcrConfidencePolicy and move the review threshold.
     */
    private float normaliseConfidence(float raw, String sourceObjectKey) {
        if (raw < 0.0f || raw > MAX_RAW_CONFIDENCE) {
            throw OcrProviderException.permanent(ID,
                    "PaddleOCR reported confidence " + raw + " for " + sourceObjectKey
                    + ", outside the expected 0.0-1.0 range. The serving layer's confidence scale "
                    + "has changed; normalisation here must be updated before results are trusted.",
                    null);
        }
        return raw;
    }

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static float toFloat(Object value) {
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
