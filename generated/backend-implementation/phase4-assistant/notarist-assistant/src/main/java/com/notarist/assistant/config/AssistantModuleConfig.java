package com.notarist.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assistant module configuration.
 *
 * PHASE 6A.3-FIX — ObjectMapper ownership:
 *   - Removed @Primary ObjectMapper bean (was duplicate of AiRuntimeConfig.aiRuntimeObjectMapper).
 *   - Module customization now via Jackson2ObjectMapperBuilderCustomizer — applied globally
 *     to the auto-configured ObjectMapper without claiming @Primary ownership.
 *   - AiRuntimeConfig in notarist-runtime remains the single @Primary ObjectMapper authority.
 *
 * The customizer ensures Instant fields in SSE payloads serialize as ISO-8601 strings,
 * consistent with the platform-wide Jackson configuration.
 */
@Configuration
public class AssistantModuleConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer assistantJacksonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
