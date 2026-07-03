package com.notarist.auth.config;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth module wiring.
 *
 * PostgreSQL datasource beans (postgresDataSource, postgresJdbcTemplate) are
 * provided centrally by notarist-infra PostgresConnectionConfig — removing them
 * here prevents BeanDefinitionStoreException on startup.
 *
 * SessionTokenRepositoryImpl injects @Qualifier("postgresJdbcTemplate") which
 * resolves to the HikariCP-backed bean from PostgresConnectionConfig.
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
