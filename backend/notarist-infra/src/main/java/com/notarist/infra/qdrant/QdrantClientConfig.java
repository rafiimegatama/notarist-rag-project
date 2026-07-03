package com.notarist.infra.qdrant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(QdrantProperties.class)
public class QdrantClientConfig {

    /**
     * Dedicated RestTemplate for Qdrant with explicit connect + read timeouts.
     * Separate from any other RestTemplate beans to avoid timeout cross-contamination.
     */
    @Bean("qdrantRestTemplate")
    public RestTemplate qdrantRestTemplate(QdrantProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeoutMs());
        factory.setReadTimeout((int) props.searchTimeoutMs());
        RestTemplate template = new RestTemplate(factory);

        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            template.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("api-key", props.apiKey());
                return execution.execute(request, body);
            });
        }

        return template;
    }
}
