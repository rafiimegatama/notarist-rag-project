package com.notarist.kase.api;

import com.notarist.core.api.response.PageResponse;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.kase.api.response.CaseResponse;
import com.notarist.kase.api.response.TimelineResponse;
import com.notarist.kase.api.rest.CaseController;
import com.notarist.kase.api.rest.CaseExceptionHandler;
import com.notarist.kase.application.command.ChangeCaseStatusCommand;
import com.notarist.kase.application.command.OpenCaseCommand;
import com.notarist.kase.application.port.in.CaseManagementUseCase;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.exception.CaseNotFoundException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.valueobject.CaseId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MVC tests: the controller wired to a mocked use case and the Case exception advice.
 * Verifies HTTP mapping, the shared envelope, validation, and the status codes the advice assigns —
 * without a database or a Spring context.
 */
class CaseControllerTest {

    private CaseManagementUseCase useCase;
    private MockMvc mockMvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = mock(CaseManagementUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CaseController(useCase))
                .setControllerAdvice(new CaseExceptionHandler())
                .build();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(userId, tenantId, "STAFF"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    private CaseResponse sampleCase(UUID caseId, String state) {
        return new CaseResponse(caseId, "12/V/2026", "FIDUSIA", tenantId, userId, null,
                state, false, null, List.of(), "2026-07-15T00:00:00Z", null);
    }

    @Test
    @DisplayName("POST /api/v1/cases → 201 with the created case and a SUCCESS envelope")
    void createReturns201() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(useCase.openCase(any(OpenCaseCommand.class)))
                .thenReturn(sampleCase(caseId, "CASE_CREATED"));

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseNumber\":\"12/V/2026\",\"caseType\":\"FIDUSIA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.data.state").value("CASE_CREATED"));

        ArgumentCaptor<OpenCaseCommand> cmd = ArgumentCaptor.forClass(OpenCaseCommand.class);
        org.mockito.Mockito.verify(useCase).openCase(cmd.capture());
        assertThat(cmd.getValue().caller().tenantId()).isEqualTo(tenantId);
        assertThat(cmd.getValue().caseNumber()).isEqualTo("12/V/2026");
    }

    @Test
    @DisplayName("POST with a blank caseNumber → 400 from bean validation")
    void createValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseNumber\":\"\",\"caseType\":\"FIDUSIA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with an unknown caseType → 400 (mapped IllegalArgumentException)")
    void createBadEnum() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseNumber\":\"12/V/2026\",\"caseType\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CASE_BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/cases/{id} → 200")
    void getReturns200() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(useCase.getCase(any(CaseId.class), any(CallerContext.class)))
                .thenReturn(sampleCase(caseId, "UPLOADING"));

        mockMvc.perform(get("/api/v1/cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("UPLOADING"));
    }

    @Test
    @DisplayName("GET a missing case → 404 CASE_NOT_FOUND")
    void getNotFound() throws Exception {
        when(useCase.getCase(any(CaseId.class), any(CallerContext.class)))
                .thenThrow(new CaseNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/cases/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH an illegal transition → 409 CASE_ILLEGAL_TRANSITION")
    void patchIllegalTransition() throws Exception {
        when(useCase.changeStatus(any(ChangeCaseStatusCommand.class)))
                .thenThrow(new IllegalTransitionException("Case: illegal transition CASE_CREATED → FINALIZED"));

        mockMvc.perform(patch("/api/v1/cases/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetState\":\"FINALIZED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CASE_ILLEGAL_TRANSITION"));
    }

    @Test
    @DisplayName("PATCH a legal transition → 200 with the moved case")
    void patchHappyPath() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(useCase.changeStatus(any(ChangeCaseStatusCommand.class)))
                .thenReturn(sampleCase(caseId, "UPLOADING"));

        mockMvc.perform(patch("/api/v1/cases/" + caseId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetState\":\"UPLOADING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("UPLOADING"));
    }

    @Test
    @DisplayName("GET /api/v1/cases → 200 with a page envelope")
    void listReturnsPage() throws Exception {
        when(useCase.listCases(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(CallerContext.class)))
                .thenReturn(PageResponse.of(List.of(sampleCase(UUID.randomUUID(), "CASE_CREATED")), 0, 20, 1));

        mockMvc.perform(get("/api/v1/cases").param("status", "CASE_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/cases/{id}/timeline → 200")
    void getTimeline() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(useCase.getTimeline(any(CaseId.class), any(CallerContext.class)))
                .thenReturn(new TimelineResponse(UUID.randomUUID(), caseId, "ACTIVE", false, 0,
                        "2026-07-15T00:00:00Z", List.of()));

        mockMvc.perform(get("/api/v1/cases/" + caseId + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
