package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.domain.model.AnswerConfidence;
import com.notarist.assistant.domain.model.AssistantSafetyMode;
import org.springframework.stereotype.Service;

/**
 * Evaluates retrieval grounding BEFORE the LLM is called.
 * Determines AnswerConfidence from grounding score + chunk count + safety mode.
 *
 * Thresholds (STRICT mode):
 *   HIGH        >= 0.75
 *   MEDIUM      >= 0.50
 *   LOW         >= 0.25
 *   INSUFFICIENT <  0.25  OR chunkCount == 0
 *
 * BALANCED and EXPLORATORY modes apply lower thresholds, permitting
 * the LLM to proceed with less evidence but with mandatory warnings.
 */
@Service
public class GroundingEvaluator {

    public AnswerConfidence evaluate(float groundingScore, int chunkCount, AssistantSafetyMode safetyMode) {
        if (chunkCount == 0) return AnswerConfidence.INSUFFICIENT;

        return switch (safetyMode) {
            case STRICT      -> evaluateStrict(groundingScore);
            case BALANCED    -> evaluateBalanced(groundingScore);
            case EXPLORATORY -> evaluateExploratory(groundingScore);
        };
    }

    private AnswerConfidence evaluateStrict(float score) {
        if (score >= 0.75f) return AnswerConfidence.HIGH;
        if (score >= 0.50f) return AnswerConfidence.MEDIUM;
        if (score >= 0.25f) return AnswerConfidence.LOW;
        return AnswerConfidence.INSUFFICIENT;
    }

    private AnswerConfidence evaluateBalanced(float score) {
        if (score >= 0.65f) return AnswerConfidence.HIGH;
        if (score >= 0.40f) return AnswerConfidence.MEDIUM;
        if (score >= 0.20f) return AnswerConfidence.LOW;
        return AnswerConfidence.INSUFFICIENT;
    }

    private AnswerConfidence evaluateExploratory(float score) {
        if (score >= 0.55f) return AnswerConfidence.HIGH;
        if (score >= 0.30f) return AnswerConfidence.MEDIUM;
        if (score >= 0.10f) return AnswerConfidence.LOW;
        return AnswerConfidence.INSUFFICIENT;
    }
}
