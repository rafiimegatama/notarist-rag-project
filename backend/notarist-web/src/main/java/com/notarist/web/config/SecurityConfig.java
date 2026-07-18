package com.notarist.web.config;

import com.notarist.auth.infrastructure.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * <h2>Method security is ON</h2>
 *
 * {@code @EnableMethodSecurity} makes {@code @PreAuthorize} actually evaluate. Without it those
 * annotations are inert decoration — the JWT filter was already putting {@code ROLE_*} authorities
 * into the {@code SecurityContext}, but nothing ever consulted them, so the platform's five roles
 * (STAFF / NOTARIS / PPAT_OFFICER / PIMPINAN / ADMIN) existed on paper and enforced nothing.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Loopback + RFC1918 private ranges + the IPv6 loopback: a Prometheus server on the host, in
     * the same docker network, or in the same cluster. Public clients are still rejected.
     */
    private static final List<IpAddressMatcher> PRIVATE_NETWORKS = List.of(
            new IpAddressMatcher("127.0.0.0/8"),
            new IpAddressMatcher("::1/128"),
            new IpAddressMatcher("10.0.0.0/8"),
            new IpAddressMatcher("172.16.0.0/12"),
            new IpAddressMatcher("192.168.0.0/16"));

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .cors(cors -> {})  // uses the corsConfigurationSource bean below
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                // Liveness/readiness must answer any prober (k8s, docker healthcheck, LB).
                .requestMatchers("/actuator/health/**").permitAll()
                // Scrape + metrics endpoints: unauthenticated (a Prometheus scraper carries no JWT
                // and was silently getting 401 — F21) but NOT public. Restricted to private/loopback
                // source addresses so an accidentally internet-exposed actuator port does not hand
                // out internal telemetry.
                .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                    .access(privateNetworkOnly())

                // Operator probes: a Cloud Run / k8s / docker prober carries no JWT, and a health
                // check that fails when auth fails is a health check that reports the wrong outage.
                // These return "OK" / "READY" only — no tenant data.
                .requestMatchers("/ops/health/live", "/ops/health/ready").permitAll()

                // Everything else under /ops is ADMIN-ONLY.
                //
                // This surface performs DESTRUCTIVE, tenant-scoped operations — DLQ replay, queue
                // replay, reindex — plus a full operational dashboard. It previously fell through to
                // `anyRequest().authenticated()`, so any logged-in user, down to the lowest STAFF
                // role, could invoke it; and because the endpoint took tenantId as a query
                // parameter, they could aim it at ANY tenant, straight past the row-level-security
                // isolation the rest of the system relies on.
                //
                // This is the second of two independent layers: OperationalHealthEndpoint also
                // carries a class-level @PreAuthorize("hasRole('ADMIN')"). The URL rule alone is
                // fragile (it stops protecting anything the moment a path changes); the annotation
                // alone would be silently inert if method security were ever switched off. Keep both.
                .requestMatchers("/ops/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Allows the request only when its source address is loopback or RFC1918 private.
     *
     * <p>Caveat: this evaluates the TCP peer address ({@code getRemoteAddr()}). Behind a reverse
     * proxy that peer is the proxy, so the check degrades to "the proxy is internal" — it is a
     * defence-in-depth restriction on top of network/firewall policy, not a substitute for it.
     * X-Forwarded-For is deliberately NOT trusted here: it is client-spoofable.
     */
    private AuthorizationManager<RequestAuthorizationContext> privateNetworkOnly() {
        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            boolean allowed = isPrivateAddress(request.getRemoteAddr());
            if (!allowed) {
                log.warn("Denied actuator scrape of {} from non-private address {}",
                        request.getRequestURI(), request.getRemoteAddr());
            }
            return new AuthorizationDecision(allowed);
        };
    }

    private boolean isPrivateAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return false;
        return PRIVATE_NETWORKS.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    /**
     * Browser clients (the Expo web dev server) run on a different origin than the API, so
     * without CORS headers every preflight is rejected and the web app cannot even reach
     * /auth/login. Native mobile clients send no Origin header and are unaffected. The
     * allowlist is env-overridable (CORS_ALLOWED_ORIGINS, comma-separated); the default
     * covers only local dev servers — a production web origin must be set explicitly.
     * Credentials stay disabled: auth travels in the Authorization header, not cookies.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:8081,http://localhost:19006}")
            String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * JwtAuthenticationFilter is registered explicitly on the Security filter chain above.
     * Without this, Spring Boot would also auto-register the bean as a generic servlet filter
     * at default (lowest) precedence, running it a second time after authorization checks.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableAutoRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
