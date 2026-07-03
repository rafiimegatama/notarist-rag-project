package com.notarist.assistant.api.response;

import com.notarist.assistant.domain.model.AnswerConfidence;
import com.notarist.assistant.domain.model.AssistantSafetyMode;
import com.notarist.assistant.domain.model.ResponseTrace;

import java.util.List;

/**
 * Structured assistant response — segmented into discrete sections.
 *
 * Segments:
 *   answerText        — main answer text (may be fallback message if downgraded)
 *   citationSection   — formatted citation text for display
 *   confidenceSection — human-readable grounding level description
 *   warnings          — hallucination/low-grounding warning strings
 *   followUpQuestions — 1-3 suggested follow-up questions
 *
 * Never a single giant blob — segments allow clients to render
 * citation panels, confidence badges, and warning banners independently.
 */
public record AssistantResponse(
        ResponseTrace trace,
        String status,
        String answerText,
        String citationSection,
        String confidenceSection,
        List<String> warnings,
        List<String> followUpQuestions,
        AnswerConfidence confidence,
        float groundingScore,
        boolean hallucinationWarning,
        boolean downgraded,
        AssistantSafetyMode safetyMode,
        List<CitationDto> citations,
        long processingTimeMs,
        String errorMessage
) {
    public static AssistantResponse success(
            ResponseTrace trace,
            String answerText,
            String citationSection,
            String confidenceSection,
            List<String> warnings,
            List<String> followUpQuestions,
            AnswerConfidence confidence,
            float groundingScore,
            boolean hallucinationWarning,
            boolean downgraded,
            AssistantSafetyMode safetyMode,
            List<CitationDto> citations,
            long processingTimeMs) {

        return new AssistantResponse(
                trace, "SUCCESS",
                answerText, citationSection, confidenceSection,
                List.copyOf(warnings), List.copyOf(followUpQuestions),
                confidence, groundingScore,
                hallucinationWarning, downgraded, safetyMode,
                List.copyOf(citations),
                processingTimeMs, null);
    }

    public static AssistantResponse error(ResponseTrace trace, String errorMessage) {
        return new AssistantResponse(
                trace, "ERROR",
                null, null, null,
                List.of(), List.of(),
                AnswerConfidence.INSUFFICIENT, 0f,
                false, false, AssistantSafetyMode.STRICT,
                List.of(),
                0L, errorMessage);
    }
}
