package com.notarist.kase.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.kase.api.response.ReminderResponse;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.port.in.ReminderUseCase;
import com.notarist.kase.application.query.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deterministic case reminders, grouped by deadline proximity. No LLM, no scheduler. */
@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/reminders")
@Tag(name = "Reminders", description = "Deterministic case deadline reminders")
@SecurityRequirement(name = "bearerAuth")
public class ReminderController {

    private final ReminderUseCase reminders;
    private final CallerContextResolver callerResolver;

    public ReminderController(ReminderUseCase reminders, CallerContextResolver callerResolver) {
        this.reminders = reminders;
        this.callerResolver = callerResolver;
    }

    @GetMapping
    @Operation(summary = "Reminders for the caller's tenant, grouped TODAY / 7 / 30 days / OVERDUE")
    public ResponseEntity<ApiResponse<ReminderResponse>> reminders(HttpServletRequest request) {
        CorrelationId correlationId = callerResolver.correlationId(request);
        CallerContext caller = callerResolver.resolve(request);
        ReminderResponse response = reminders.getReminders(caller);
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }
}
