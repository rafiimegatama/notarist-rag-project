package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.ingest.application.port.out.NerServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stub adapter for IndoBERT NER sidecar on :8082.
 * Returns deterministic stub result with PII redaction confirmed.
 * Replace with real HTTP call in Phase 2C.
 */
@Component
public class NerServiceAdapter implements NerServicePort {

    private static final Logger log = LoggerFactory.getLogger(NerServiceAdapter.class);

    private final String sidecarUrl;

    public NerServiceAdapter(
            @Value("${notarist.ingest.ner.sidecar-url:http://localhost:8082}") String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
    }

    @Override
    public NerResult extractEntities(String ocrObjectKey, JenisDokumen documentType, NerConfig config) {
        log.info("[STUB] NER extractEntities objectKey={} documentType={} sidecar={}",
                ocrObjectKey, documentType, sidecarUrl);

        return new NerResult(
                ocrObjectKey.replace("notarist-ocr/", "notarist-processed/") + ".ner.json",
                Map.of("PERSON", 3, "ORGANIZATION", 2, "DATE", 5, "LOCATION", 1),
                "HYBRID",
                true,
                180L
        );
    }
}
