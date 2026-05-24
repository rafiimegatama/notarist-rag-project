# PHASE 6A.3-FIX — Security Filter Activation Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0

---

## Problem

`JwtAuthenticationFilter` (Phase 1) was registered as a Spring bean in `AuthModuleConfig` but was **never added to the Spring Security filter chain** in `SecurityConfig`. The class was complete and functional, but authentication was inactive — every request to any protected endpoint either failed with 401 (no SecurityContext populated) or passed unauthenticated (depending on Spring's default behavior with no auth provider).

The `SecurityConfig` contained an explicit TODO comment:
```java
// TODO (STEP 8B): .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

This TODO has been resolved.

---

## Filter Chain Map (Post-Fix)

```
Incoming HTTP Request
       │
       ▼
[Order=1] CorrelationIdFilter  (notarist-web, notarist-core)
       │  • Extracts or generates X-Correlation-ID → MDC.correlationId
       │  • Extracts or generates X-Trace-ID → MDC.traceId
       │  • Writes both headers back to response
       │  • Clears MDC in finally block (always)
       │
       ▼
[Spring Security] JwtAuthenticationFilter  (notarist-auth)
       │  • Reads Authorization: Bearer <token>
       │  • Validates JWT via JwtService (RSA public key)
       │  • Checks JTI against TokenDenyListPort (revocation)
       │  • Populates SecurityContextHolder with UsernamePasswordAuthenticationToken
       │  • Populates VpdContextHolder.VpdPrincipal (userId, tenantId, highestRole)
       │  • Adds userId + tenantId to MDC
       │  • Clears VpdContextHolder in finally block (always)
       │  • On invalid token: 401 Unauthorized (does NOT throw, sends error response)
       │
       ▼
[Spring Security] UsernamePasswordAuthenticationFilter (passthrough — STATELESS)
       │
       ▼
[Spring Security] Authorization check
       │  • /api/v1/auth/login, /api/v1/auth/refresh → permitAll()
       │  • /actuator/health/** → permitAll()
       │  • All other requests → authenticated()
       │
       ▼
Controller / Handler
```

---

## SecurityConfig Fix

### Before
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/metrics").permitAll()
                .anyRequest().authenticated()
            );
        // TODO (STEP 8B): .addFilterBefore(jwtAuthFilter, ...)
        return http.build();
    }
}
```

### After
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Filter jwtAuthenticationFilter;

    public SecurityConfig(
            @Qualifier("jwtAuthenticationFilter") Filter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(
                    "/actuator/health/**",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### Design Notes

- **Injected as `jakarta.servlet.Filter`** — avoids cross-module type import (`notarist-web` → `notarist-auth`). The `@Qualifier("jwtAuthenticationFilter")` matches the bean method name in `AuthModuleConfig`.
- **`/actuator/metrics` removed from `permitAll()`** — metrics endpoint now requires authentication. Load-balancer probes should use `/actuator/health/liveness` and `/actuator/health/readiness`.
- **Stateless session** — no `HttpSession` created; VPD context is per-request only.

---

## Correlation ID Propagation Across Filter Chain

### Fix Applied to `AssistantController`

Header case mismatch was found and fixed:

| Location | Before | After |
|---|---|---|
| `AssistantController.ask()` | `@RequestHeader(value = "X-Correlation-Id")` | `@RequestHeader(value = "X-Correlation-ID")` |
| `AssistantController.askStream()` | `@RequestHeader(value = "X-Correlation-Id")` | `@RequestHeader(value = "X-Correlation-ID")` |

`NotaristConstants.HEADER_CORRELATION_ID = "X-Correlation-ID"` is the canonical value. All header references now use uppercase `D`.

### Propagation Chain Verification

```
Client sends: X-Correlation-ID: <uuid>
      ↓
CorrelationIdFilter: reads NotaristConstants.HEADER_CORRELATION_ID ("X-Correlation-ID")
  → MDC.put("correlationId", correlationId.value())
  → response.setHeader("X-Correlation-ID", ...)
      ↓
JwtAuthenticationFilter: reads Authorization header, adds userId/tenantId to MDC
  (does NOT disturb correlationId MDC entry)
      ↓
AssistantController: reads @RequestHeader("X-Correlation-ID") — NOW CONSISTENT
  → uses it to create ApiMeta.of(correlationId.value())
      ↓
Logging pattern includes [%X{correlationId}] — traces across all log lines
```

---

## Endpoint Coverage Matrix

| Endpoint | Auth Required | Notes |
|---|---|---|
| `POST /api/v1/auth/login` | NO | Public — token issuance |
| `POST /api/v1/auth/refresh` | NO | Public — token refresh |
| `GET /actuator/health/**` | NO | Load balancer probes |
| `GET /actuator/health/liveness` | NO | K8s liveness probe |
| `GET /actuator/health/readiness` | NO | K8s readiness probe |
| `GET /actuator/prometheus` | YES | Internal scrape only |
| `GET /actuator/metrics` | YES | Restricted |
| `POST /api/v1/assistant/ask` | YES | JWT required |
| `POST /api/v1/assistant/ask/stream` | YES | JWT required |
| `GET /api/v1/documents/**` | YES | JWT + VPD |
| `POST /api/v1/ingest/**` | YES | JWT + VPD |
| `GET /api/v1/search/**` | YES | JWT + VPD |

---

## Files Modified

| File | Change |
|---|---|
| `backend-skeleton/notarist-web/.../SecurityConfig.java` | Injected `jwtAuthenticationFilter`; added `.addFilterBefore()`; tightened actuator exposure |
| `phase4-assistant/.../AssistantController.java` | Fixed `X-Correlation-Id` → `X-Correlation-ID` in both endpoints |
