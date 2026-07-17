package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.Map;

/**
 * Output port for Named Entity Recognition extraction.
 * Implemented by IndoBertNerAdapter in notarist-runtime (real IndoBERT HTTP call).
 */
public interface NerServicePort {

    NerResult extractEntities(String ocrObjectKey, JenisDokumen documentType, NerConfig config);

    /**
     * @param processedObjectKey  MinIO key for the .ner.json output file
     * @param entitiesExtracted   entity type → count (e.g. "PERSON" → 3)
     * @param engineUsed          model variant identifier
     * @param piiRedacted         must be true before chunking stage is allowed to proceed
     * @param durationMs          extraction wall-clock time
     */
    record NerResult(
            String processedObjectKey,
            Map<String, Integer> entitiesExtracted,
            String engineUsed,
            boolean piiRedacted,
            long durationMs
    ) {}

    record NerConfig(
            float minEntityConfidence,
            boolean requirePiiRedaction,
            String modelVariant
    ) {
        public static NerConfig defaultConfig() {
            return new NerConfig(0.7f, true, "indobert-base");
        }
    }
}
