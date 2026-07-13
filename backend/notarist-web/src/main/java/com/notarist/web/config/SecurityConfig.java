package com.notarist.web.config;

import com.notarist.auth.infrastructure.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
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
