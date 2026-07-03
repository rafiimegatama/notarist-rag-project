package com.notarist.auth.config;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.infrastructure.security.JwtAuthenticationFilter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auth module wiring. Configures PostgreSQL datasource for session token storage
 * and exposes JwtAuthenticationFilter as a named bean for SecurityConfig to register.
 */
@Configuration
public class AuthModuleConfig {

    @Bean("postgresDataSource")
    public DataSource postgresDataSource(
            @Value("${notarist.datasource.postgres.url}") String url,
            @Value("${notarist.datasource.postgres.username}") String username,
            @Value("${notarist.datasource.postgres.password}") String password,
            @Value("${notarist.datasource.postgres.pool-size:10}") int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(20_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(1_200_000);
        config.setPoolName("notarist-postgres-pool");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource postgresDataSource) {
        return new JdbcTemplate(postgresDataSource);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            TokenDenyListPort tokenDenyListPort) {
        return new JwtAuthenticationFilter(jwtService, tokenDenyListPort);
    }
}
