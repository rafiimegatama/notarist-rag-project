package com.notarist.web.filter;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.core.util.NotaristConstants;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Extracts or generates X-Correlation-ID and X-Trace-ID headers.
 * Propagates both to MDC for structured logging and to response headers.
 * Runs first in filter chain (Order(1)).
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationIdValue = httpRequest.getHeader(NotaristConstants.HEADER_CORRELATION_ID);
        CorrelationId correlationId = (correlationIdValue != null && !correlationIdValue.isBlank())
            ? CorrelationId.of(correlationIdValue)
            : CorrelationId.generate();

        String traceIdValue = httpRequest.getHeader(NotaristConstants.HEADER_TRACE_ID);
        TraceId traceId = (traceIdValue != null && !traceIdValue.isBlank())
            ? TraceId.of(traceIdValue)
            : TraceId.generate();

        MDC.put("correlationId", correlationId.value());
        MDC.put("traceId", traceId.value());
        MDC.put("requestPath", httpRequest.getRequestURI());

        httpResponse.setHeader(NotaristConstants.HEADER_CORRELATION_ID, correlationId.value());
        httpResponse.setHeader(NotaristConstants.HEADER_TRACE_ID, traceId.value());

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("traceId");
            MDC.remove("requestPath");
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }
}
