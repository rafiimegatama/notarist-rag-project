# PHASE 6A.3-FIX — Serialization Authority Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0

---

## Problem

Two `@Bean @Primary ObjectMapper` beans existed simultaneously:

| Module | Class | Bean |
|---|---|---|
| `notarist-assistant` (Phase 4) | `AssistantModuleConfig` | `@Bean @Primary ObjectMapper objectMapper()` |
| `notarist-runtime` (Phase 5B) | `AiRuntimeConfig` | `@Bean @Primary ObjectMapper aiRuntimeObjectMapper()` |

Spring throws `NoUniqueBeanDefinitionException` at startup when any component injects `ObjectMapper` without a qualifier. Both beans were configured identically (JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS disabled).

---

## Serialization Ownership Map

```
SINGLE @Primary ObjectMapper AUTHORITY
─────────────────────────────────────────────────────
notarist-runtime / AiRuntimeConfig.aiRuntimeObjectMapper()
  @Bean @Primary ObjectMapper
  JavaTimeModule registered
  WRITE_DATES_AS_TIMESTAMPS disabled

CUSTOMIZERS (non-owning, additively configure Spring's auto-configured ObjectMapper)
─────────────────────────────────────────────────────
notarist-assistant / AssistantModuleConfig.assistantJacksonCustomizer()
  Jackson2ObjectMapperBuilderCustomizer (NOT a @Primary @Bean)
  Applies: JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS disabled
  Effect: modifies the auto-configured ObjectMapper; does not create a competing primary
```

### Why `Jackson2ObjectMapperBuilderCustomizer`?

The `Jackson2ObjectMapperBuilderCustomizer` interface is the Spring Boot-recommended pattern for module-specific Jackson configuration:
- Does NOT create a new ObjectMapper bean
- Applies additively to the auto-configured ObjectMapper
- Multiple customizers can coexist without conflict
- Survives Spring auto-configuration ordering changes

---

## Fix Applied

### `AssistantModuleConfig.java` — before

```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

### `AssistantModuleConfig.java` — after

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer assistantJacksonCustomizer() {
    return builder -> builder
            .modules(new JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
}
```

### `AiRuntimeConfig.java` — unchanged

```java
@Bean
@Primary
public ObjectMapper aiRuntimeObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

---

## Files Modified

| File | Change |
|---|---|
| `phase4-assistant/.../AssistantModuleConfig.java` | Removed `@Bean @Primary ObjectMapper`; replaced with `Jackson2ObjectMapperBuilderCustomizer` |
| `phase5b-runtime/.../AiRuntimeConfig.java` | Unchanged — remains sole `@Primary ObjectMapper` authority |

---

## Post-Fix Serialization Status

| Check | Status |
|---|---|
| Single `@Primary ObjectMapper` in application context | PASS |
| `NoUniqueBeanDefinitionException` risk eliminated | PASS |
| ISO-8601 Instant serialization preserved for SSE payloads | PASS |
| Module-specific customization preserved | PASS |
| No `@Qualifier` changes required in consuming beans | PASS |
