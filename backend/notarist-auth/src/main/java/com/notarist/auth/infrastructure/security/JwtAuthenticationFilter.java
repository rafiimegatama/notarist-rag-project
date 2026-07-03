package com.notarist.auth.infrastructure.security;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.application.service.JwtService;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.core.util.NotaristConstants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenDenyListPort tokenDenyListPort;

    public JwtAuthenticationFilter(JwtService jwtService, TokenDenyListPort tokenDenyListPort) {
        this.jwtService = jwtService;
        this.tokenDenyListPort = tokenDenyListPort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.validateAndParseClaims(token);

            String jti = claims.getId();
            if (jti != null && tokenDenyListPort.isDenied(jti)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                return;
            }

            String userId = claims.getSubject();
            String tenantId = claims.get("tenantId", String.class);
            @SuppressWarnings("unchecked")
            List<String> roleNames = claims.get("roles", List.class);

            Collection<SimpleGrantedAuthority> authorities = roleNames.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String highestRole = resolveHighestRole(roleNames);
            VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                    UUID.fromString(userId),
                    UUID.fromString(tenantId),
                    highestRole
            ));

            MDC.put("userId", userId);
            MDC.put("tenantId", tenantId);

            filterChain.doFilter(request, response);

        } catch (JwtService.InvalidTokenException e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        } finally {
            VpdContextHolder.clear();
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }

    private static final List<String> ROLE_PRIORITY =
            List.of("ADMIN", "PIMPINAN", "PPAT_OFFICER", "NOTARIS", "STAFF");

    private String resolveHighestRole(List<String> roleNames) {
        for (String candidate : ROLE_PRIORITY) {
            if (roleNames.contains(candidate)) return candidate;
        }
        return "STAFF";
    }
}
