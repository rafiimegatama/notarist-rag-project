package com.notarist.infra.qdrant;

import com.notarist.infra.resilience.DegradedModeRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Spring Boot Actuator health indicator for Qdrant.
 * Calls GET /readyz — Qdrant's lightweight readiness probe.
 */
@Component
public class QdrantHealthIndicator implements HealthIndicator {

    private final RestTemplate         qdrantRestTemplate;
    private final QdrantProperties     props;
    private final DegradedModeRegistry degradedMode;

    public QdrantHealthIndicator(
            @Qualifier("qdrantRestTemplate") RestTemplate qdrantRestTemplate,
            QdrantProperties props,
            DegradedModeRegistry degradedMode) {
        this.qdrantRestTemplate = qdrantRestTemplate;
        this.props              = props;
        this.degradedMode       = degradedMode;
    }

    @Override
    public Health health() {
        try {
            ResponseEntity<String> response = qdrantRestTemplate.getForEntity(
                    props.baseUrl() + "/readyz", String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.QDRANT);
                return Health.up()
                        .withDetail("endpoint", props.baseUrl())
                        .withDetail("collection", props.collectionName())
                        .withDetail("degraded", false)
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", response.getStatusCode())
                        .build();
            }
        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.QDRANT, e.getMessage());
            return Health.down(e)
                    .withDetail("endpoint", props.baseUrl())
                    .withDetail("degraded", true)
                    .build();
        }
    }
}
