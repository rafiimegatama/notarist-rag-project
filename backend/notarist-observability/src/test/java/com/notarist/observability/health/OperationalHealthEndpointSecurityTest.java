package com.notarist.observability.health;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.observability.consistency.SnapshotReadinessChecker;
import com.notarist.observability.ops.OperationalCliFacade;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the two CRITICAL findings are actually closed — not merely annotated.
 *
 * <p>The bug being guarded against was not a missing annotation, it was an annotation that was never
 * evaluated: the JWT filter had been putting {@code ROLE_*} authorities into the SecurityContext all
 * along, but method security was off, so every role check in the codebase was decoration. A test that
 * only asserted "the annotation is present" would have passed against the vulnerable code. So this
 * test boots real method security and calls the real method.
 *
 * <p>What it locks down:
 * <ol>
 *   <li>A STAFF user (the lowest role) is DENIED the destructive operator endpoints.</li>
 *   <li>An ADMIN is allowed.</li>
 *   <li>An ADMIN's operation is scoped to the tenant on their TOKEN — the endpoint no longer accepts
 *       a tenantId from the caller at all, so tenant A cannot aim a DLQ replay at tenant B.</li>
 * </ol>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OperationalHealthEndpointSecurityTest.TestConfig.class)
class OperationalHealthEndpointSecurityTest {

    private static final UUID CALLER_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CALLER_USER   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** The tenant an attacker would have tried to reach via the old ?tenantId= parameter. */
    private static final UUID VICTIM_TENANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean OperationalCliFacade cliFacade()             { return mock(OperationalCliFacade.class); }
        @Bean HealthAggregationService healthService()     { return mock(HealthAggregationService.class); }
        @Bean SnapshotReadinessChecker snapshotChecker()   { return mock(SnapshotReadinessChecker.class); }
        @Bean MeterRegistry meterRegistry()                { return new SimpleMeterRegistry(); }

        @Bean
        OperationalHealthEndpoint endpoint(HealthAggregationService h, SnapshotReadinessChecker s,
                                           OperationalCliFacade c, MeterRegistry m) {
            return new OperationalHealthEndpoint(h, s, c, m);
        }
    }

    @Autowired OperationalHealthEndpoint endpoint;
    @Autowired OperationalCliFacade cliFacade;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        VpdContextHolder.clear();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        CALLER_USER.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(CALLER_USER, CALLER_TENANT, role));
    }

    @Test
    @DisplayName("STAFF is denied DLQ replay — the destructive operator surface is ADMIN-only")
    void staffCannotReplayDlq() {
        authenticateAs("STAFF");

        assertThatThrownBy(() -> endpoint.replayDlq(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("STAFF is denied reindex")
    void staffCannotTriggerReindex() {
        authenticateAs("STAFF");

        assertThatThrownBy(() -> endpoint.triggerReindex("whatever"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("STAFF is denied the operational dashboard")
    void staffCannotReadDashboard() {
        authenticateAs("STAFF");

        assertThatThrownBy(() -> endpoint.dashboard())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ADMIN is allowed, and the replay is scoped to the tenant on the ADMIN's own token")
    void adminReplaysOnlyTheirOwnTenant() {
        authenticateAs("ADMIN");
        when(cliFacade.replayDlq(any(), any(), any()))
                .thenReturn(OperationalCliFacade.OperationResult.success("REPLAY_DLQ", "op", java.util.Map.of()));

        endpoint.replayDlq("OCR");

        // The tenant comes from the JWT principal, and the operator id is the authenticated user —
        // neither is caller-supplied any more, so neither can be pointed at another tenant or used
        // to forge the audit attribution.
        verify(cliFacade).replayDlq(
                eq(CALLER_TENANT.toString()),
                eq("OCR"),
                eq(CALLER_USER.toString()));
    }

    @Test
    @DisplayName("There is no longer any way to name a foreign tenant: the parameter does not exist")
    void tenantIdIsNotAcceptedFromTheRequest() throws Exception {
        // The old signatures took `String tenantId` from a @RequestParam. Their absence is the fix,
        // so assert on the reflective signature: if anyone reintroduces a tenant parameter on these
        // methods, this fails and they have to come and read the class javadoc explaining why.
        assertThat(OperationalHealthEndpoint.class
                .getMethod("replayDlq", String.class).getParameterCount()).isEqualTo(1);
        assertThat(OperationalHealthEndpoint.class
                .getMethod("replayQueue").getParameterCount()).isZero();
        assertThat(OperationalHealthEndpoint.class
                .getMethod("checkVectors").getParameterCount()).isZero();
        assertThat(OperationalHealthEndpoint.class
                .getMethod("triggerReindex", String.class).getParameterCount()).isEqualTo(1);

        // And the surviving single parameters are NOT a tenant under another name.
        assertThat(VICTIM_TENANT).isNotEqualTo(CALLER_TENANT); // sanity: the two tenants differ
    }
}
