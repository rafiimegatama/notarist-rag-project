package com.notarist.infra.qdrant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(QdrantProperties.class)
public class QdrantClientConfig {

    /**
     * Dedicated RestTemplate for Qdrant with explicit connect + read timeouts.
     * Separate from any other RestTemplate beans to avoid timeout cross-contamination.
     */
    @Bean("qdrantRestTemplate")
    public RestTemplate qdrantRestTemplate(QdrantProperties props, RestTemplateBuilder builder) {
        RestTemplate template = builder
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(props.searchTimeoutMs()))
                .build();

        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            template.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("api-key", props.apiKey());
                return execution.execute(request, body);
            });
        }

        return template;
    }
}
