package com.notarist.verification.api;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.verification.api.response.VerificationResponse;
import com.notarist.verification.api.response.VerificationSummaryResponse;
import com.notarist.verification.api.rest.VerificationController;
import com.notarist.verification.api.rest.VerificationExceptionHandler;
import com.notarist.verification.api.support.CallerContextResolver;
import com.notarist.verification.api.support.DecisionTranslator;
import com.notarist.verification.application.command.UpdateChecklistItemCommand;
import com.notarist.verification.application.port.in.VerificationUseCase;
import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.domain.exception.IllegalVerificationTransitionException;
import com.notarist.verification.domain.exception.VerificationNotFoundException;
import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.VerificationId;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MVC tests: the controller wired to a mocked use case, the real decision translator and
 * the verification exception advice. Verifies HTTP mapping, the shared envelope, the response shape,
 * decision translation and the status codes the advice assigns — no database.
 */
class VerificationControllerTest {

    private VerificationUseCase useCase;
    private MockMvc mockMvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = mock(VerificationUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VerificationController(useCase, new CallerContextResolver(),
                        new DecisionTranslator()))
                .setControllerAdvice(new VerificationExceptionHandler())
                .build();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(userId, tenantId, "NOTARIS"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    private Verification sample() {
        ChecklistItem item = ChecklistItem.create(ItemId.generate(), ChecklistCategory.AUTHORITY,
                "Authority clause", true, CheckType.AUTOMATIC, 0);
        return Verification.start(VerificationId.generate(), bundleId, tenantId, List.of(item));
    }

    @Test
    @DisplayName("GET /bundles/{id}/verification → 200 with status, progress, checklist, categories, summary")
    void getShape() throws Exception {
        when(useCase.getVerification(any(), any(CallerContext.class))).thenReturn(VerificationResponse.from(sample()));

        mockMvc.perform(get("/api/v1/bundles/" + bundleId + "/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.progress.total").value(1))
                .andExpect(jsonPath("$.data.checklist[0].category").value("AUTHORITY"))
                .andExpect(jsonPath("$.data.categories[0].category").value("AUTHORITY"))
                .andExpect(jsonPath("$.data.summary.completable").exists());
    }

    @Test
    @DisplayName("GET a missing verification → 404 VERIFICATION_NOT_FOUND")
    void getMissing() throws Exception {
        when(useCase.getVerification(any(), any(CallerContext.class)))
                .thenThrow(new VerificationNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/bundles/" + bundleId + "/verification"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST checklist APPROVED translates to PASS")
    void postApprovedTranslatesToPass() throws Exception {
        when(useCase.updateChecklistItem(any(UpdateChecklistItemCommand.class)))
                .thenReturn(VerificationResponse.from(sample()));
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bundles/" + bundleId + "/verification/checklist/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateChecklistItemCommand> cmd = ArgumentCaptor.forClass(UpdateChecklistItemCommand.class);
        verify(useCase).updateChecklistItem(cmd.capture());
        assertThat(cmd.getValue().decision()).isEqualTo(Decision.PASS);
    }

    @Test
    @DisplayName("POST checklist REJECTED with no comment carries a non-blank default reason")
    void postRejectedDefaultsComment() throws Exception {
        when(useCase.updateChecklistItem(any(UpdateChecklistItemCommand.class)))
                .thenReturn(VerificationResponse.from(sample()));
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bundles/" + bundleId + "/verification/checklist/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateChecklistItemCommand> cmd = ArgumentCaptor.forClass(UpdateChecklistItemCommand.class);
        verify(useCase).updateChecklistItem(cmd.capture());
        assertThat(cmd.getValue().decision()).isEqualTo(Decision.FAIL);
        assertThat(cmd.getValue().comment()).isNotBlank();
    }

    @Test
    @DisplayName("PATCH an illegal transition → 409 VERIFICATION_ILLEGAL_TRANSITION")
    void patchIllegal() throws Exception {
        when(useCase.changeStatus(any()))
                .thenThrow(new IllegalVerificationTransitionException("illegal PENDING → VERIFIED"));

        mockMvc.perform(patch("/api/v1/bundles/" + bundleId + "/verification/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"VERIFIED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_ILLEGAL_TRANSITION"));
    }

    @Test
    @DisplayName("PATCH with an unknown status → 400 (mapped IllegalArgumentException)")
    void patchBadStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/bundles/" + bundleId + "/verification/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /summary → 200 with progress")
    void getSummary() throws Exception {
        when(useCase.getSummary(any(), any(CallerContext.class)))
                .thenReturn(VerificationSummaryResponse.from(sample()));

        mockMvc.perform(get("/api/v1/bundles/" + bundleId + "/verification/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.total").value(1))
                .andExpect(jsonPath("$.data.completable").exists());
    }
}
