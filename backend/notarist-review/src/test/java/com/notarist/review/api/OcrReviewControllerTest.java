package com.notarist.review.api;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.review.api.response.OcrReviewResponse;
import com.notarist.review.api.rest.OcrReviewController;
import com.notarist.review.api.rest.OcrReviewExceptionHandler;
import com.notarist.review.api.support.CallerContextResolver;
import com.notarist.review.api.support.FieldDecisionTranslator;
import com.notarist.review.application.command.ReviewFieldCommand;
import com.notarist.review.application.port.in.OcrReviewUseCase;
import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.exception.IllegalReviewTransitionException;
import com.notarist.review.domain.exception.ReviewNotFoundException;
import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.ReviewId;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MVC tests: the controller wired to a mocked use case, the real decision translator and
 * the review exception advice. Verifies HTTP mapping, the shared envelope, the frontend-compatible
 * response shape, the decision translation and the status codes the advice assigns — no database.
 */
class OcrReviewControllerTest {

    private OcrReviewUseCase useCase;
    private MockMvc mockMvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = mock(OcrReviewUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OcrReviewController(useCase, new CallerContextResolver(),
                        new FieldDecisionTranslator()))
                .setControllerAdvice(new OcrReviewExceptionHandler())
                .build();
        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(userId, tenantId, "STAFF"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    private OcrReviewResponse sampleResponse() {
        FieldReview f = FieldReview.extracted(FieldId.generate(), "NIK", "NIK", "123", 0.98,
                BoundingBox.of(1, 0.32, 0.18, 0.4, 0.05), 0);
        OcrReview r = OcrReview.start(ReviewId.generate(), documentId, tenantId, "KTP.pdf", 1,
                false, true, 0.9, List.of(f), List.of());
        return OcrReviewResponse.from(r);
    }

    @Test
    @DisplayName("GET /documents/{id}/ocr → 200 with the frontend-compatible shape")
    void getReviewShape() throws Exception {
        when(useCase.getReview(any(), any(CallerContext.class))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/documents/" + documentId + "/ocr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.documentName").value("KTP.pdf"))
                .andExpect(jsonPath("$.data.signatureDetected").value(true))
                .andExpect(jsonPath("$.data.fields[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.fields[0].bbox.w").value(0.4))   // frontend keys: x,y,w,h
                .andExpect(jsonPath("$.data.fields[0].confidence").value(0.98))
                .andExpect(jsonPath("$.data.authorityTimeline").isArray());
    }

    @Test
    @DisplayName("GET a missing review → 404 OCR_REVIEW_NOT_FOUND")
    void getReviewMissing() throws Exception {
        when(useCase.getReview(any(), any(CallerContext.class)))
                .thenThrow(new ReviewNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/documents/" + documentId + "/ocr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("OCR_REVIEW_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT field APPROVED (no value) translates to MANUAL_ACCEPTED")
    void putApprovedTranslatesToManualAccept() throws Exception {
        when(useCase.reviewField(any(ReviewFieldCommand.class))).thenReturn(sampleResponse());
        UUID fieldId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/documents/" + documentId + "/ocr/fields/" + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReviewFieldCommand> cmd = ArgumentCaptor.forClass(ReviewFieldCommand.class);
        verify(useCase).reviewField(cmd.capture());
        assertThat(cmd.getValue().decision()).isEqualTo(FieldDecision.MANUAL_ACCEPTED);
        assertThat(cmd.getValue().caller().tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("PUT field REJECTED with no reason still carries a non-blank default reason")
    void putRejectedDefaultsReason() throws Exception {
        when(useCase.reviewField(any(ReviewFieldCommand.class))).thenReturn(sampleResponse());
        UUID fieldId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/documents/" + documentId + "/ocr/fields/" + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReviewFieldCommand> cmd = ArgumentCaptor.forClass(ReviewFieldCommand.class);
        verify(useCase).reviewField(cmd.capture());
        assertThat(cmd.getValue().decision()).isEqualTo(FieldDecision.REJECTED);
        assertThat(cmd.getValue().reason()).isNotBlank();
    }

    @Test
    @DisplayName("PUT field with an edited value translates to CORRECTED")
    void putWithValueTranslatesToCorrected() throws Exception {
        when(useCase.reviewField(any(ReviewFieldCommand.class))).thenReturn(sampleResponse());
        UUID fieldId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/documents/" + documentId + "/ocr/fields/" + fieldId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"NEEDS_CHECK\",\"value\":\"3174\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReviewFieldCommand> cmd = ArgumentCaptor.forClass(ReviewFieldCommand.class);
        verify(useCase).reviewField(cmd.capture());
        assertThat(cmd.getValue().decision()).isEqualTo(FieldDecision.CORRECTED);
        assertThat(cmd.getValue().correctedValue()).isEqualTo("3174");
    }

    @Test
    @DisplayName("PATCH an illegal transition → 409 OCR_REVIEW_ILLEGAL_TRANSITION")
    void patchIllegalTransition() throws Exception {
        when(useCase.changeStatus(any()))
                .thenThrow(new IllegalReviewTransitionException("illegal PENDING → VERIFIED"));

        mockMvc.perform(patch("/api/v1/documents/" + documentId + "/ocr/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"VERIFIED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OCR_REVIEW_ILLEGAL_TRANSITION"));
    }

    @Test
    @DisplayName("PATCH with an unknown status → 400 (mapped IllegalArgumentException)")
    void patchBadStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/documents/" + documentId + "/ocr/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OCR_REVIEW_BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /summary → 200 with progress counters")
    void getSummary() throws Exception {
        when(useCase.getSummary(any(), any(CallerContext.class)))
                .thenReturn(com.notarist.review.api.response.OcrReviewSummaryResponse.from(
                        rehydratedForSummary()));

        mockMvc.perform(get("/api/v1/documents/" + documentId + "/ocr/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.total").value(1))
                .andExpect(jsonPath("$.data.progress.remaining").value(1));
    }

    private OcrReview rehydratedForSummary() {
        FieldReview f = FieldReview.extracted(FieldId.generate(), "NIK", "NIK", "123", 0.98,
                BoundingBox.of(1, 0.1, 0.1, 0.2, 0.05), 0);
        return OcrReview.start(ReviewId.generate(), documentId, tenantId, "KTP.pdf", 1,
                false, true, 0.9, List.of(f), List.of());
    }
}
