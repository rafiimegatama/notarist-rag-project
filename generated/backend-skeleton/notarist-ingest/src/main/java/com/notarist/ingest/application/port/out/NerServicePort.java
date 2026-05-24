package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.Map;

/** Port for IndoBERT NER sidecar — :8082. Adapter enforces 30s timeout with 3x retry. */
public interface NerServicePort {

    NerResult extractEntities(String ocrObjectKey, JenisDokumen documentType, NerConfig config);

    record NerConfig(String engine, boolean enablePiiRedaction, boolean ruleBasedFirst) {
        public static NerConfig defaultConfig() {
            return new NerConfig("HYBRID", true, true);
        }
    }

    record NerResult(
        String nerObjectKey,
        Map<String, Integer> entitiesExtracted,
        String engineUsed,
        boolean piiRedacted,
        long processingMs
    ) {}
}
