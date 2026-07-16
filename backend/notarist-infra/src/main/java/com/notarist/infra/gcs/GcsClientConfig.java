package com.notarist.infra.gcs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public Storage storage(GcsProperties props) throws IOException {
        GoogleCredentials credentials = resolveCredentials(props);

        HttpTransportOptions transport = HttpTransportOptions.newBuilder()
                .setConnectTimeout(props.connectTimeoutMs())
                .setReadTimeout(props.readTimeoutMs())
                .build();

        StorageOptions.Builder builder = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setTransportOptions(transport);

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
