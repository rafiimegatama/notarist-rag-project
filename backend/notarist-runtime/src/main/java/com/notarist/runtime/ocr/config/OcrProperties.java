package com.notarist.runtime.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * All OCR configuration, bound from {@code notarist.ocr.*}.
 *
 * <p>Note what is NOT here: any reference to {@code ModelRegistry}. The old PaddleOcrAdapter pulled
 * its endpoint out of the shared AI ModelRegistry, which meant OCR config lived in the same object
 * as the LLM and embedding config, and adding an OCR engine meant editing an enum shared with the
 * LLM. OCR now owns its own configuration end to end.
 *
 * <p><b>No localhost defaults.</b> The endpoint has no default value anywhere in the code. An engine
 * with no configured endpoint fails at STARTUP with a message naming the missing property, rather
 * than silently defaulting to {@code http://localhost:8081} and then failing at the first upload —
 * in a container, where there is nothing on localhost:8081 and never was.
 */
@ConfigurationProperties(prefix = "notarist.ocr")
public class OcrProperties {

    // NOTE: the active provider is NOT bound here. It is resolved in OcrProviderRegistry from
    // notarist.runtime.ocr.provider (env OCR_PROVIDER) via @Value, exactly as ProviderRegistry
    // resolves the LLM/embedding/reranker providers. One selection mechanism across the whole
    // runtime beats two that drift apart.

    /** Wall-clock budget for a single document, across ALL retry attempts. */
    private long timeoutMs = 120_000;

    /** Probe every registered provider at startup and log the outcome. */
    private boolean probeOnStartup = true;

    /** Fail startup if the selected provider's health probe fails. */
    private boolean failFastOnUnhealthyProvider = false;

    private Retry retry = new Retry();
    private Batch batch = new Batch();

    /** Per-engine settings, keyed by provider id. */
    private Map<String, ProviderConfig> providers = Map.of();

    // ---------------------------------------------------------------------

    public static class Retry {
        /** Total attempts, including the first. 1 = no retry. */
        private int maxAttempts = 3;

        private long initialBackoffMs = 1_000;
        private long maxBackoffMs = 15_000;
        private double multiplier = 2.0;

        /**
         * Randomise backoff by ±this fraction. With a batch of documents failing together against a
         * saturated GPU, identical backoff means they all retry in the same instant and saturate it
         * again. Jitter spreads them out.
         */
        private double jitterFactor = 0.2;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public void setInitialBackoffMs(long v) { this.initialBackoffMs = v; }
        public long getMaxBackoffMs() { return maxBackoffMs; }
        public void setMaxBackoffMs(long v) { this.maxBackoffMs = v; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double v) { this.multiplier = v; }
        public double getJitterFactor() { return jitterFactor; }
        public void setJitterFactor(double v) { this.jitterFactor = v; }
    }

    public static class Batch {
        /** Route batches to providers that support them. */
        private boolean enabled = true;

        /**
         * Documents/pages pushed at the engine at once. {@code 0} = derive it from the DETECTED
         * hardware profile (see OcrBatchSizer). Never derive it from a GPU model name.
         */
        private int maxSize = 0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    public static class ProviderConfig {
        /**
         * Base URL of the engine. REQUIRED for the active provider — no default, no localhost.
         * e.g. http://ocr:8081, https://ocr-prod-xxxx.a.run.app, https://vision.googleapis.com
         */
        private String endpoint;

        /** Path appended to the endpoint. Engine-specific; PaddleOCR serves /predict/ocr_system. */
        private String path;

        /** Per-request HTTP timeout. Falls back to the top-level timeoutMs when unset. */
        private Long timeoutMs;

        /** API key, for the cloud engines. Injected from Secret Manager; never committed. */
        private String apiKey;

        /** Anything engine-specific that does not deserve a first-class field yet. */
        private Map<String, String> options = Map.of();

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Map<String, String> getOptions() { return options; }
        public void setOptions(Map<String, String> options) { this.options = options; }
    }

    // ---------------------------------------------------------------------

    /**
     * Config for one engine, or a clear failure naming the missing property.
     *
     * <p>Note that {@code PaddleOcrProvider} deliberately does NOT call this from its constructor:
     * throwing there would turn "OCR is unconfigured" into "the application will not boot", taking
     * auth and document endpoints down with it. It records the problem and surfaces it through
     * health() and extract() instead.
     *
     * <p>This method is for a provider that genuinely cannot degrade — one where operating with a
     * missing setting would be unsafe rather than merely broken. Reach for it deliberately.
     */
    public ProviderConfig requireProviderConfig(String providerId) {
        ProviderConfig config = providers.get(providerId);
        if (config == null) {
            throw new IllegalStateException(
                    "No configuration for OCR provider '" + providerId + "'. Add "
                    + "notarist.ocr.providers." + providerId + ".endpoint");
        }
        if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new IllegalStateException(
                    "OCR provider '" + providerId + "' has no endpoint. Set "
                    + "notarist.ocr.providers." + providerId + ".endpoint (env: OCR_BASE_URL). "
                    + "There is deliberately no localhost default — a default that works on a laptop "
                    + "and silently fails in a container is worse than a startup error.");
        }
        return config;
    }

    /** Effective per-request timeout for an engine. */
    public long effectiveTimeoutMs(String providerId) {
        ProviderConfig config = providers.get(providerId);
        if (config != null && config.getTimeoutMs() != null) {
            return config.getTimeoutMs();
        }
        return timeoutMs;
    }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isProbeOnStartup() { return probeOnStartup; }
    public void setProbeOnStartup(boolean probeOnStartup) { this.probeOnStartup = probeOnStartup; }
    public boolean isFailFastOnUnhealthyProvider() { return failFastOnUnhealthyProvider; }
    public void setFailFastOnUnhealthyProvider(boolean v) { this.failFastOnUnhealthyProvider = v; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }
}
