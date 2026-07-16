package com.notarist.review.api.support;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.core.util.NotaristConstants;
import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.valueobject.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link CallerContext} from the authenticated principal + correlation id. The auth Role
 * names are identical to the Review-context Role names by design, so this is a 1:1 map from the JWT's
 * highest role — no new claim, no change to authentication.
 *
 * <p>Explicit bean name: other bounded contexts scan a class of the same simple name, so the default
 * (class-derived) name would collide under the root {@code @ComponentScan("com.notarist")}.
 */
@Component("reviewCallerContextResolver")
public class CallerContextResolver {

    public CallerContext resolve(HttpServletRequest request) {
        VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal in context"));
        return new CallerContext(
                principal.userId(),
                principal.tenantId(),
                Role.valueOf(principal.highestRole()),
                correlationId(request));
    }

    public CorrelationId correlationId(HttpServletRequest request) {
        String header = request.getHeader(NotaristConstants.HEADER_CORRELATION_ID);
        return (header != null && !header.isBlank()) ? CorrelationId.of(header) : CorrelationId.generate();
    }
}
