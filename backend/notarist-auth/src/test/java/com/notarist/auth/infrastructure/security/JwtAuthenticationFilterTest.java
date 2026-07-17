package com.notarist.auth.infrastructure.security;

import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.application.service.JwtService;
import com.notarist.core.security.VpdContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String JTI = UUID.randomUUID().toString();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private JwtService jwtService;
    private TokenDenyListPort denyList;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        denyList = mock(TokenDenyListPort.class);
        filter = new JwtAuthenticationFilter(jwtService, denyList);

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(JTI);
        when(claims.getSubject()).thenReturn(USER_ID.toString());
        when(claims.get("tenantId", String.class)).thenReturn(TENANT.toString());
        when(claims.get("roles", List.class)).thenReturn(List.of("NOTARIS"));
        when(jwtService.validateAndParseClaims("good.jwt")).thenReturn(claims);
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    /**
     * The whole point of the deny-list: a token that is still cryptographically valid and unexpired
     * must be refused once its jti has been revoked by a logout.
     */
    @Test
    @DisplayName("a revoked jti is rejected with 401 and never reaches the application")
    void revokedTokenIsRejected() throws Exception {
        when(denyList.isDenied(JTI)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken("good.jwt"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        assertThat(VpdContextHolder.get()).isEmpty();
    }

    @Test
    @DisplayName("a valid, non-revoked token establishes the tenant identity from the signed claims")
    void validTokenEstablishesTenantIdentity() throws Exception {
        when(denyList.isDenied(JTI)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // The principal must exist DURING the chain — the filter clears it in a finally block.
        FilterChain chain = (req, res) -> {
            VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get().orElseThrow();
            assertThat(principal.userId()).isEqualTo(USER_ID);
            assertThat(principal.tenantId()).isEqualTo(TENANT);
            assertThat(principal.highestRole()).isEqualTo("NOTARIS");
        };

        filter.doFilter(requestWithToken("good.jwt"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * VpdContextHolder is a ThreadLocal on a pooled request thread. If it survived the request, the
     * next request on that thread would inherit the previous caller's tenant.
     */
    @Test
    @DisplayName("the VPD principal is cleared after the request, leaving no ThreadLocal residue")
    void vpdContextIsClearedAfterRequest() throws Exception {
        when(denyList.isDenied(JTI)).thenReturn(false);

        filter.doFilter(requestWithToken("good.jwt"), new MockHttpServletResponse(),
                mock(FilterChain.class));

        assertThat(VpdContextHolder.get())
                .as("a leaked principal would hand the next request this caller's tenant")
                .isEmpty();
    }

    @Test
    @DisplayName("an invalid signature is rejected with 401 and sets no identity")
    void invalidTokenIsRejected() throws Exception {
        when(jwtService.validateAndParseClaims("bad.jwt"))
                .thenThrow(new JwtService.InvalidTokenException("bad signature", null));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken("bad.jwt"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        assertThat(VpdContextHolder.get()).isEmpty();
    }

    @Test
    @DisplayName("a request with no bearer token is passed through unauthenticated (chain decides)")
    void noTokenPassesThroughUnauthenticated() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertThat(VpdContextHolder.get()).isEmpty();
    }
}
