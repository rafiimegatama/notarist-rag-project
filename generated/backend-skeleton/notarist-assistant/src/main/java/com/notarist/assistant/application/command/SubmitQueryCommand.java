package com.notarist.assistant.application.command;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.SessionId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.search.domain.model.SearchIntent;

import java.util.UUID;

public record SubmitQueryCommand(
    SessionId sessionId,
    UUID userId,
    UUID tenantId,
    String userRole,
    String queryText,
    boolean stream,
    int topK,
    SearchIntent searchIntent,
    CorrelationId correlationId,
    TraceId traceId
) {}
