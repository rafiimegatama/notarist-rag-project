package com.notarist.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Assistant module configuration.
 *
 * Registers an ObjectMapper that:
 *   - Serializes Instant as ISO-8601 string (not epoch array) for SSE payloads
 *   - Used by ResponseStreamer and SseEvent JSON serialization
 *
 * No additional datasource: assistant module does not own any DB schema in Phase 4.
 * Conversation memory is in-process (ConversationMemoryService ConcurrentHashMap).
 */
@Configuration
public class AssistantModuleConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
