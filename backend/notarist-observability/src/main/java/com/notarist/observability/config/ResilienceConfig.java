package com.notarist.observability.config;

import com.notarist.observability.circuit.CircuitBreakerRegistry;
import com.notarist.observability.degradation.OperationalDegradationHierarchy;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Wires observability and resilience beans.
 *
 * Provides:
 *   - RestTemplate for consistency checkers (shared, read-only use)
 *   - Startup degradation evaluator (runs once after bean init completes)
 *
 * CircuitBreakerRegistry and OperationalDegradationHierarchy are @Component beans;
 * no explicit @Bean declaration needed — Spring auto-detects them.
 *
 * Resilience settings:
 *   - HTTP connect timeout: 5s
 *   - HTTP read timeout: 10s (observability calls are lightweight)
 *   - Circuit breaker thresholds: 3 failures → OPEN, 30s cooldown, 2 probes → CLOSED
 *     (configured as constants in CircuitBreakerRegistry — no external config needed)
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    private final CircuitBreakerRegistry          circuitBreakers;
    private final OperationalDegradationHierarchy degradationHierarchy;

    public ResilienceConfig(CircuitBreakerRegistry circuitBreakers,
                             OperationalDegradationHierarchy degradationHierarchy) {
        this.circuitBreakers      = circuitBreakers;
        this.degradationHierarchy = degradationHierarchy;
    }

    @Bean("observabilityRestTemplate")
    public RestTemplate observabilityRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Performs an initial degradation evaluation at startup.
     * Ensures the first health endpoint call reflects a computed state, not the startup default.
     */
    @Bean
    public OperationalDegradationHierarchy.DegradationLevel initialDegradationLevel() {
        OperationalDegradationHierarchy.DegradationLevel level = degradationHierarchy.evaluate();
        log.info("ResilienceConfig: initial degradation level = {}", level);
        return level;
    }
}
