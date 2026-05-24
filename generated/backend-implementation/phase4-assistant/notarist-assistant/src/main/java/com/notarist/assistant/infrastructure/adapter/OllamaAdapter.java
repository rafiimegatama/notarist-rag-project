package com.notarist.assistant.infrastructure.adapter;

import com.notarist.assistant.application.port.out.LlmPort;
import com.notarist.assistant.domain.model.LlmRequest;
import com.notarist.assistant.domain.model.LlmResponse;
import com.notarist.assistant.domain.model.LlmStreamChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ollama LLM adapter stub — Phase 4.
 *
 * Real Ollama HTTP inference (POST /api/chat + streaming) deferred to Phase 5.
 * Returns a deterministic stub response in Indonesian so the rest of the pipeline
 * (HallucinationGuard, ResponseStreamer, CitationSync) can be validated end-to-end.
 *
 * isAvailable() returns false — callers must handle LLM-unavailable paths gracefully.
 */
@Component
public class OllamaAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaAdapter.class);

    private static final String STUB_RESPONSE =
            "Berdasarkan dokumen yang tersedia, berikut adalah informasi yang dapat saya sampaikan. " +
            "Layanan LLM (Ollama) belum tersedia pada fase ini [Sumber: stub]. " +
            "Jawaban ini merupakan respons stub dari Phase 4 yang akan digantikan oleh inferensi nyata pada Phase 5. " +
            "Silakan pastikan server Ollama berjalan dan model yang dibutuhkan telah di-pull.";

    @Override
    public LlmResponse invoke(LlmRequest request) {
        log.debug("OllamaAdapter stub invoke — traceId={} model={}", request.traceId(), request.model());
        return LlmResponse.stub(STUB_RESPONSE);
    }

    @Override
    public void stream(LlmRequest request, Consumer<LlmStreamChunk> chunkConsumer) {
        log.debug("OllamaAdapter stub stream — traceId={}", request.traceId());
        // Emit stub as single chunk + done signal
        chunkConsumer.accept(new LlmStreamChunk(UUID.randomUUID().toString(), STUB_RESPONSE, false, 0));
        chunkConsumer.accept(new LlmStreamChunk(UUID.randomUUID().toString(), "", true, 1));
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
