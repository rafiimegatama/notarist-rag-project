package com.notarist.kase.api;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.api.response.ReminderResponse;
import com.notarist.kase.api.rest.ReminderController;
import com.notarist.kase.api.support.CallerContextResolver;
import com.notarist.kase.application.port.in.ReminderUseCase;
import com.notarist.kase.application.query.CallerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderControllerTest {

    private ReminderUseCase reminders;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reminders = mock(ReminderUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReminderController(reminders, new CallerContextResolver()))
                .build();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "NOTARIS"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    @Test
    void remindersReturns200WithBuckets() throws Exception {
        ReminderResponse body = new ReminderResponse(
                "2026-07-15T00:00:00Z", 1,
                Map.of("OVERDUE", 1, "TODAY", 0, "WITHIN_7_DAYS", 0, "WITHIN_30_DAYS", 0),
                List.of(new ReminderResponse.ReminderItem(
                        UUID.randomUUID(), "1/V/2026", "SKMHT", "UPLOADING", "SKMHT_DEADLINE",
                        "2026-05-31T00:00:00Z", -45)),
                List.of(), List.of(), List.of());
        when(reminders.getReminders(any(CallerContext.class))).thenReturn(body);

        mockMvc.perform(get("/api/v1/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.overdue[0].reminderType").value("SKMHT_DEADLINE"))
                .andExpect(jsonPath("$.data.overdue[0].daysUntilDue").value(-45));
    }
}
