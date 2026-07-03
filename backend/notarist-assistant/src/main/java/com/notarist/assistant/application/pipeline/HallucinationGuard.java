package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.domain.model.AnswerConfidence;
import com.notarist.assistant.domain.model.AssistantSafetyMode;
import com.notarist.assistant.domain.model.GuardResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-LLM gate that evaluates the response for hallucination risk.
 *
 * Decision tree:
 *   INSUFFICIENT confidence         → downgrade (no LLM response passes)
 *   LOW + STRICT mode               → downgrade
 *   LOW + BALANCED/EXPLORATORY      → passWithWarnings
 *   Unsupported claims + STRICT     → downgrade
 *   Unsupported claims + BALANCED   → passWithWarnings
 *   Unsupported claims + EXPLORATORY→ passWithWarnings
 *   Else                            → pass (with any remaining warnings from grounding)
 */
@Service
public class HallucinationGuard {

    private static final Logger log = LoggerFactory.getLogger(HallucinationGuard.class);

    private static final String FALLBACK_MESSAGE =
            "Saya tidak menemukan dasar dokumen yang cukup untuk memastikan jawaban ini. " +
            "Silakan konsultasikan dengan notaris atau PPAT yang berwenang.";

    public GuardResult guard(
            String llmResponseText,
            AnswerConfidence preLlmConfidence,
            List<String> unsupportedClaims,
            AssistantSafetyMode safetyMode) {

        List<String> warnings = new ArrayList<>();

        // Mandatory downgrade on INSUFFICIENT — regardless of mode
        if (preLlmConfidence == AnswerConfidence.INSUFFICIENT) {
            log.warn("HallucinationGuard: INSUFFICIENT grounding — response downgraded");
            return GuardResult.downgrade(
                    "Grounding tidak cukup (INSUFFICIENT): " + FALLBACK_MESSAGE,
                    List.of("Jawaban tidak dapat dipastikan karena tidak ada dokumen yang relevan ditemukan."));
        }

        // LOW grounding in STRICT mode — downgrade
        if (preLlmConfidence == AnswerConfidence.LOW && safetyMode == AssistantSafetyMode.STRICT) {
            log.warn("HallucinationGuard: LOW grounding in STRICT mode — downgraded");
            return GuardResult.downgrade(
                    "Grounding lemah (LOW) dalam mode STRICT: " + FALLBACK_MESSAGE,
                    List.of("Dokumen yang relevan ditemukan namun tidak cukup untuk mendukung jawaban pasti."));
        }

        // LOW grounding in non-STRICT — add caution warning
        if (preLlmConfidence == AnswerConfidence.LOW) {
            warnings.add("Peringatan: Grounding dokumen rendah. Jawaban mungkin tidak sepenuhnya akurat.");
        }

        // Unsupported claims in STRICT mode — downgrade
        if (!unsupportedClaims.isEmpty() && safetyMode == AssistantSafetyMode.STRICT) {
            log.warn("HallucinationGuard: {} unsupported claims in STRICT mode — downgraded",
                    unsupportedClaims.size());
            warnings.addAll(unsupportedClaims);
            return GuardResult.downgrade(
                    "Klaim tanpa sumber terdeteksi dalam mode STRICT",
                    warnings);
        }

        // Unsupported claims in non-STRICT — add warnings but pass
        if (!unsupportedClaims.isEmpty()) {
            warnings.addAll(unsupportedClaims);
            log.debug("HallucinationGuard: {} unsupported claims — warnings added", unsupportedClaims.size());
        }

        return warnings.isEmpty()
                ? GuardResult.pass(preLlmConfidence)
                : GuardResult.passWithWarnings(warnings, preLlmConfidence);
    }

    public String getFallbackMessage() {
        return FALLBACK_MESSAGE;
    }
}
