package com.notarist.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notarist.runtime.capability.GpuAwarenessConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Wires all AI runtime beans.
 *
 * Provides:
 *   - RestTemplate with per-call timeouts for synchronous adapters (OCR, embedding, reranker)
 *   - ObjectMapper with JavaTimeModule for JSON parsing in adapters
 *
 * OllamaRuntimeAdapter uses OkHttp directly (streaming); does NOT use this RestTemplate.
 *
 * Pool isolations (OCR/Embedding/Inference/Reranker) are registered as @Component beans;
 * no explicit @Bean wiring needed here — Spring auto-detects them.
 *
 * GPU profile is read from GpuAwarenessConfig.activeHardwareProfile() and logged at startup.
 * Pool resizing for GPU is not automated — configure application.yml corePoolSize overrides instead.
 */
@Configuration
public class AiRuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(AiRuntimeConfig.class);

    private final GpuAwarenessConfig.HardwareProfile hardwareProfile;

    public AiRuntimeConfig(GpuAwarenessConfig.HardwareProfile hardwareProfile) {
        this.hardwareProfile = hardwareProfile;
        log.info("AiRuntimeConfig: initialising with hardwareProfile={}", hardwareProfile);
    }

    @Bean
    public RestTemplate aiRuntimeRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    @Primary
    public ObjectMapper aiRuntimeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
