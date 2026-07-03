package com.notarist.infra.minio;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioClientConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(props.connectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(props.readTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(props.writeTimeoutMs(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)   // retries managed by NotaristRetryPolicy
                .build();

        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .httpClient(httpClient)
                .build();
    }
}
