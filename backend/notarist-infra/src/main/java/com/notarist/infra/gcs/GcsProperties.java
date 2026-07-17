package com.notarist.infra.gcs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Cloud Storage connection properties.
 * Bound from {@code notarist.storage.gcs.*} in application.yaml.
 *
 * <p>Single-bucket model: every pipeline stage stores its objects in the SAME bucket, namespaced
 * by key prefix ({@code notarist-raw/…}, {@code notarist-ocr/…}, …). This mirrors exactly how the
 * object keys are already built by the ingest pipeline, so nothing about the key layout changes
 * moving off MinIO — only the backing store does.
 *
 * <p>Credentials follow Google Application Default Credentials (ADC). On Cloud Run that is the
 * attached runtime service account (Workload Identity) — no key file, no localhost endpoint. For
 * local development, point {@code GOOGLE_APPLICATION_CREDENTIALS} at a service-account key, or set
 * {@code notarist.storage.gcs.credentials-path}.
 *
 * <p><b>emulatorHost</b> switches the whole client to a GCS emulator (fake-gcs-server). It is bound
 * from the {@code STORAGE_EMULATOR_HOST} variable that Google's own client libraries use — the same
 * name the OCR/NER sidecars already honour — so one variable moves the entire stack together. When
 * it is set, credentials are not resolved at all, which is what lets the pipeline run with no ADC.
 */
@ConfigurationProperties(prefix = "notarist.storage.gcs")
public record GcsProperties(
        String projectId,
        String bucket,
        String credentialsPath,
        String signingServiceAccount,
        String emulatorHost,
        int connectTimeoutMs,
        int readTimeoutMs,
        Boolean autoCreateBucket
) {
    public GcsProperties {
        if (bucket == null || bucket.isBlank())
            throw new IllegalStateException("notarist.storage.gcs.bucket is required (set GCS_BUCKET)");
        // projectId may be null: the GCS client resolves it from ADC / the metadata server on
        // Cloud Run. It is only required when running against ADC that carries no project.
        if (connectTimeoutMs <= 0) connectTimeoutMs = 10_000;
        if (readTimeoutMs <= 0)    readTimeoutMs    = 60_000;
        // Boolean (not boolean): absent means "on", not "false".
        if (autoCreateBucket == null) autoCreateBucket = Boolean.TRUE;
        // Normalise "" (an unset ${STORAGE_EMULATOR_HOST:} placeholder) to null so every caller can
        // test one thing — emulatorEnabled() — instead of each re-deciding what blank means.
        if (emulatorHost != null && emulatorHost.isBlank()) emulatorHost = null;
    }

    /** The single bucket that backs all pipeline stages (raw/ocr/processed/chunk/export prefixes). */
    public String defaultBucket() {
        return bucket;
    }

    /** True when the client should talk to a GCS emulator instead of Google. */
    public boolean emulatorEnabled() {
        return emulatorHost != null;
    }

    /** Emulator base URL, scheme-qualified. STORAGE_EMULATOR_HOST is conventionally set both with
     *  and without a scheme (host:port or http://host:port); accept either. */
    public String emulatorBaseUrl() {
        if (emulatorHost == null) return null;
        String host = emulatorHost.endsWith("/")
                ? emulatorHost.substring(0, emulatorHost.length() - 1)
                : emulatorHost;
        return host.startsWith("http://") || host.startsWith("https://") ? host : "http://" + host;
    }
}
