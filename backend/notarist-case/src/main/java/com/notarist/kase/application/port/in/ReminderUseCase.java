package com.notarist.kase.application.port.in;

import com.notarist.kase.api.response.ReminderResponse;
import com.notarist.kase.application.query.CallerContext;

/** Deterministic reminder calculation. Pure query — no scheduler, no side effects, no LLM. */
public interface ReminderUseCase {

    ReminderResponse getReminders(CallerContext caller);
}
