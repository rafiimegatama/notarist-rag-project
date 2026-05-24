package com.notarist.auth.config;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth module wiring.
 *
 * PHASE 6A.3-FIX:
 *   - Removed @Bean("postgresDataSource") — owned by notarist-infra PostgresConnectionConfig
 *   - Removed @Bean postgresJdbcTemplate — owned by notarist-infra PostgresConnectionConfig
 *   - Namespace was notarist.datasource.postgres.* (orphaned) — removed with the beans
 *   - Retains JwtAuthenticationFilter bean for injection into SecurityConfig
 *
 * JwtAuthenticationFilter is NOT self-registering (@Component) — SecurityConfig
 * must explicitly call .addFilterBefore() to activate it.
 */
@Configuration
public class AuthModuleConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            TokenDenyListPort tokenDenyListPort) {
        return new JwtAuthenticationFilter(jwtService, tokenDenyListPort);
    }
}
