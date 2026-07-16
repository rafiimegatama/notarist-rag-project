package com.notarist.runtime.provider;

import com.notarist.assistant.application.port.out.LlmPort;
import com.notarist.assistant.domain.model.LlmRequest;
import com.notarist.assistant.domain.model.LlmResponse;
import com.notarist.assistant.domain.model.LlmStreamChunk;
import com.notarist.runtime.provider.registry.RuntimeRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * The single {@link LlmPort} bean the application sees — the {@code InferencePort} of the target
 * architecture. Delegates every call to the registry-selected {@link InferenceProvider}, so the
 * assistant orchestrator (business logic) is completely decoupled from which LLM backend runs.
 *
 * <p>Provider is resolved per call (not cached) so an {@code LLM_PROVIDER} change takes effect on
 * restart without any special reload path, and a future hot-swap remains a registry concern only.
 */
@Component
public class RegistryLlmPort implements LlmPort {

    private final RuntimeRegistry registry;

    public RegistryLlmPort(RuntimeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        return registry.llm().invoke(request);
    }

    @Override
    public void stream(LlmRequest request, Consumer<LlmStreamChunk> chunkConsumer) {
        registry.llm().stream(request, chunkConsumer);
    }

    @Override
    public boolean isAvailable() {
        return registry.llm().isAvailable();
    }

    @Override
    public void openStream(String traceId) {
        registry.llm().openStream(traceId);
    }

    @Override
    public boolean cancelStream(String traceId) {
        return registry.llm().cancelStream(traceId);
    }

    @Override
    public void closeStream(String traceId) {
        registry.llm().closeStream(traceId);
    }
}
