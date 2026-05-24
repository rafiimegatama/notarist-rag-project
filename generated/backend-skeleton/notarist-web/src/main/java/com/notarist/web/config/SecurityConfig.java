package com.notarist.web.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security filter chain configuration.
 *
 * PHASE 6A.3-FIX — JWT filter activation:
 *   - JwtAuthenticationFilter (registered by AuthModuleConfig) is now wired into the
 *     filter chain via addFilterBefore(). Authentication is active for all protected endpoints.
 *   - Injected as jakarta.servlet.Filter to avoid cross-module type import from notarist-auth.
 *   - The @Qualifier("jwtAuthenticationFilter") matches AuthModuleConfig's @Bean method name.
 *
 * Filter chain order:
 *   1. CorrelationIdFilter (Order=1) — extracts/generates X-Correlation-ID, sets MDC
 *   2. JwtAuthenticationFilter — validates Bearer token, populates SecurityContext + VpdContext
 *   3. Spring Security machinery
 *
 * Public endpoints: /api/v1/auth/login, /api/v1/auth/refresh, liveness/readiness probes.
 * All other requests require a valid JWT.
 *
 * Actuator: /actuator/health/** is public for load-balancer probes.
 * /actuator/prometheus is restricted — expose only via internal network in prod.
 */
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
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh").permitAll()
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
