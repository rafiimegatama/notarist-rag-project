package com.notarist.infra.gcs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Builds the singleton {@link Storage} client used by every storage operation.
 *
 * <p><b>Credentials resolution (no localhost, no static keys on Cloud Run):</b>
 * <ol>
 *   <li>{@code credentials-path} set → load that service-account key (local dev / CI).</li>
 *   <li>otherwise → Application Default Credentials. On Cloud Run that is the attached runtime
 *       service account via Workload Identity.</li>
 *   <li>{@code signing-service-account} set → wrap the above in {@link ImpersonatedCredentials}
 *       so V4 signed-URL signing goes through the IAM {@code signBlob} API. This is what lets
 *       Cloud Run mint signed upload URLs without ever holding a private key.</li>
 * </ol>
 *
 * <p>All three credential types implement {@code ServiceAccountSigner}, so {@code storage.signUrl}
 * can produce V4 signatures directly from the {@link Storage} client's own credentials.
 */
@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GcsClientConfig.class);

    // Read/write scope on GCS, required when we build credentials explicitly (ADC already carries
    // cloud-platform scope on Cloud Run; explicit key files start unscoped).
    private static final List<String> STORAGE_RW_SCOPE =
            List.of("https://www.googleapis.com/auth/devstorage.read_write");

    // @Lazy: the Storage client resolves ADC/service-account credentials at creation, which throws
    // if none are present. Deferring creation to first actual GCS use lets the app boot (and serve
    // DB-backed endpoints) in environments without GCS credentials; the GcsBucketProvisioner and
    // health indicator surface any real GCS outage loudly on first touch. Every injection point of
    // Storage is also @Lazy so no eager consumer forces this open at startup.
    @Bean
    @Lazy
    public Storage storage(GcsProperties props) throws IOException {
        HttpTransportOptions transport = HttpTransportOptions.newBuilder()
                .setConnectTimeout(props.connectTimeoutMs())
                .setReadTimeout(props.readTimeoutMs())
                .build();

        StorageOptions.Builder builder = StorageOptions.newBuilder()
                .setTransportOptions(transport);

        if (props.emulatorEnabled()) {
            // Emulator: point the JSON API at fake-gcs and resolve NO credentials.
            //
            // This is the whole reason the branch exists. resolveCredentials() calls
            // GoogleCredentials.getApplicationDefault(), which THROWS when no ADC is present, and
            // because that happens inside this @Lazy bean the failure surfaced as a degraded GCS on
            // first touch — the pipeline could never read or write an object without a Google login.
            // An emulator has no auth to satisfy, so asking for credentials at all is wrong.
            //
            // setHost covers every JSON operation the pipeline actually performs (exists, read,
            // copy, write). Signing is the one thing it cannot cover — see
            // GcsDocumentStorageAdapter.generateUploadUrl.
            builder.setHost(props.emulatorBaseUrl())
                   .setCredentials(NoCredentials.getInstance())
                   .setProjectId(props.projectId() == null || props.projectId().isBlank()
                           ? "notarist-local"
                           : props.projectId());

            log.warn("GCS client in EMULATOR mode host={} bucket={} — no credentials, uploads are "
                            + "UNSIGNED. Never set STORAGE_EMULATOR_HOST in production.",
                    props.emulatorBaseUrl(), props.bucket());
            return builder.build().getService();
        }

        builder.setCredentials(resolveCredentials(props));

        if (props.projectId() != null && !props.projectId().isBlank()) {
            builder.setProjectId(props.projectId());
        }

        log.info("GCS client configured bucket={} projectId={} signer={}",
                props.bucket(),
                props.projectId() == null ? "<from-ADC>" : props.projectId(),
                props.signingServiceAccount() == null ? "<default-credentials>" : props.signingServiceAccount());

        return builder.build().getService();
    }

    private GoogleCredentials resolveCredentials(GcsProperties props) throws IOException {
        GoogleCredentials base;
        if (props.credentialsPath() != null && !props.credentialsPath().isBlank()) {
            try (FileInputStream in = new FileInputStream(props.credentialsPath())) {
                base = ServiceAccountCredentials.fromStream(in).createScoped(STORAGE_RW_SCOPE);
            }
            log.info("GCS using service-account key file: {}", props.credentialsPath());
        } else {
            base = GoogleCredentials.getApplicationDefault();
        }

        if (props.signingServiceAccount() != null && !props.signingServiceAccount().isBlank()) {
            // Impersonate the signing SA so V4 signing uses IAM signBlob — the Cloud Run path where
            // the runtime SA holds no private key. Requires roles/iam.serviceAccountTokenCreator.
            return ImpersonatedCredentials.create(
                    base,
                    props.signingServiceAccount(),
                    /* delegates */ null,
                    STORAGE_RW_SCOPE,
                    /* lifetime seconds */ 3600);
        }
        return base;
    }
}
