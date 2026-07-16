package com.notarist.runtime.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import com.notarist.assistant.domain.model.LlmRequest;
import com.notarist.assistant.domain.model.LlmResponse;
import com.notarist.assistant.domain.model.LlmStreamChunk;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.guard.ContextOverflowGuard;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.model.ModelRegistry;
import com.notarist.runtime.provider.InferenceProvider;
import com.notarist.runtime.provider.ProviderCapabilities;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real Ollama HTTP adapter. Replaces the Phase 4 OllamaAdapter stub.
 * Implements LlmPort from notarist-assistant.
 *
 * Non-streaming: POST /api/chat with stream=false; blocks until completion.
 * Streaming:     POST /api/chat with stream=true; line-by-line NDJSON parsing.
 *
 * Context overflow checked by ContextOverflowGuard BEFORE the API call.
 * Uses OkHttp directly because RestTemplate cannot handle NDJSON streaming.
 *
 * Degradation: on timeout or HTTP error, marks OLLAMA degraded and throws.
 * CallerRunsPolicy in InferenceQueueIsolation applies backpressure upstream.
 *
 * <p>This is the {@code ollama} {@link InferenceProvider} — one implementation behind the
 * {@code com.notarist.runtime.provider.registry.LlmRegistry}. It is no longer wired directly as the
 * application's {@code LlmPort}; {@code RegistryLlmPort} routes to whichever provider
 * {@code LLM_PROVIDER} selects. HTTP timeouts are configuration-driven (see constructor).
 */
@Component
public class OllamaRuntimeAdapter implements InferenceProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaRuntimeAdapter.class);

    private static final String PROVIDER_ID = "ollama";
    private static final String CHAT_PATH   = "/api/chat";

    private final OkHttpClient                 httpClient;
    private final ObjectMapper                 objectMapper;
    private final ModelRegistry                modelRegistry;
    private final RuntimeMetricsRegistry       metrics;
    private final RuntimeDegradationManager    degradation;
    private final StreamingCancellationManager cancellationManager;
    private final InferenceQueueIsolation      inferenceQueue;
    private final ContextOverflowGuard         overflowGuard;
    private final String                       configuredModel;

    public OllamaRuntimeAdapter(
            ModelRegistry modelRegistry,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation,
            StreamingCancellationManager cancellationManager,
            InferenceQueueIsolation inferenceQueue,
            ContextOverflowGuard overflowGuard,
            @Qualifier("aiRuntimeObjectMapper") ObjectMapper objectMapper,
            @Value("${notarist.runtime.llm.model:}") String configuredModel,
            @Value("${notarist.runtime.llm.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${notarist.runtime.llm.read-timeout-ms:60000}") int readTimeoutMs,
            @Value("${notarist.runtime.llm.write-timeout-ms:10000}") int writeTimeoutMs) {
        this.modelRegistry       = modelRegistry;
        this.metrics             = metrics;
        this.degradation         = degradation;
        this.cancellationManager = cancellationManager;
        this.inferenceQueue      = inferenceQueue;
        this.overflowGuard       = overflowGuard;
        this.objectMapper        = objectMapper;
        this.configuredModel     = configuredModel == null ? "" : configuredModel.trim();

        // Timeouts are configuration-driven (env: LLM_CONNECT_TIMEOUT_MS / LLM_READ_TIMEOUT_MS /
        // LLM_WRITE_TIMEOUT_MS). read-timeout bounds a full non-streaming generation and the gap
        // between streamed tokens, so it scales with model size / GPU throughput.
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        log.info("OllamaRuntimeAdapter: timeouts connect={}ms read={}ms write={}ms",
                connectTimeoutMs, readTimeoutMs, writeTimeoutMs);
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    /**
     * The chat model this provider serves. Provider ≠ model (Phase 4): {@code LLM_MODEL} wins; a
     * blank value falls back to the ModelRegistry default so nothing breaks if it is unset.
     */
    @Override
    public String activeModel() {
        return configuredModel.isBlank() ? modelRegistry.getLlm().modelName() : configuredModel;
    }

    /**
     * Ollama chat capabilities. Streaming/JSON-mode/tool-calling/thinking are engine features (the
     * latter two are also model-dependent — qwen3 thinks, tool-calling needs a tool-tuned model);
     * vision is left false because it is model-specific and enabled per deployment, not assumed.
     */
    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.builder()
                .streaming(true)
                .toolCalling(true)
                .jsonMode(true)
                .thinking(true)
                .build();
    }

    @Override
    public RuntimeProviderHealth health() {
        String model = activeModel();
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OLLAMA)) {
            return RuntimeProviderHealth.down(PROVIDER_ID, model, "OLLAMA runtime marked degraded");
        }
        try {
            String baseUrl = modelRegistry.getLlm().endpointUrl();
            Request req = new Request.Builder().url(baseUrl + "/api/tags").get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    return RuntimeProviderHealth.down(PROVIDER_ID, model, "GET /api/tags → HTTP " + resp.code());
                }
                return RuntimeProviderHealth.up(PROVIDER_ID, model, "reachable at " + baseUrl,
                        Map.of("endpoint", baseUrl, "streaming", true));
            }
        } catch (Exception e) {
            return RuntimeProviderHealth.down(PROVIDER_ID, model, "unreachable: " + e.getMessage());
        }
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        long startMs = System.currentTimeMillis();
        String content = generate(request.systemPrompt(), request.userPrompt(), request.traceId());
        long durationMs = System.currentTimeMillis() - startMs;
        return new LlmResponse(content, activeModel(), 0, 0, durationMs, false, false);
    }

    @Override
    public void stream(LlmRequest request, Consumer<LlmStreamChunk> chunkConsumer) {
        final int[] idx = {0};
        generateStreaming(request.systemPrompt(), request.userPrompt(), request.traceId(),
                token -> {
                    int i = idx[0]++;
                    chunkConsumer.accept(new LlmStreamChunk(request.traceId() + "-" + i, token, false, i));
                });
        chunkConsumer.accept(new LlmStreamChunk(request.traceId() + "-done", "", true, idx[0]));
    }

    /** Opens the cancellable scope before the request is queued — see StreamingCancellationManager.open. */
    @Override
    public void openStream(String traceId) {
        cancellationManager.open(traceId);
    }

    /** Cancels an in-flight or still-queued streaming inference (SSE client gone / emitter timed out). */
    @Override
    public boolean cancelStream(String traceId) {
        return cancellationManager.cancel(traceId);
    }

    @Override
    public void closeStream(String traceId) {
        cancellationManager.deregister(traceId);
    }

    @Override
    public boolean isAvailable() {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OLLAMA)) return false;
        try {
            String baseUrl = modelRegistry.getLlm().endpointUrl();
            Request req = new Request.Builder().url(baseUrl + "/api/tags").get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) {
            log.warn("OllamaRuntimeAdapter: availability check failed: {}", e.getMessage());
            return false;
        }
    }

    private String generate(String systemPrompt, String userMessage, String traceId) {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OLLAMA)) {
            log.warn("OllamaRuntimeAdapter: OLLAMA degraded — returning fallback for traceId={}", traceId);
            return fallbackResponse();
        }

        if (inferenceQueue.isSaturated()) {
            log.warn("OllamaRuntimeAdapter: inference queue saturated — rejecting traceId={}", traceId);
            throw new OllamaOverloadException("Inference queue saturated, traceId=" + traceId);
        }

        long startMs = System.currentTimeMillis();
        try {
            String result = inferenceQueue.submit(() -> callOllama(systemPrompt, userMessage, traceId, false, null)).get();
            long durationMs = System.currentTimeMillis() - startMs;
            metrics.recordInferenceLatency(ModelProvider.OLLAMA, durationMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OLLAMA, false, null);
            return result;
        } catch (Exception e) {
            handleFailure(e, traceId);
            return fallbackResponse();
        }
    }

    private void generateStreaming(String systemPrompt, String userMessage, String traceId,
                                   Consumer<String> tokenConsumer) {
        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OLLAMA)) {
            log.warn("OllamaRuntimeAdapter: OLLAMA degraded — streaming fallback traceId={}", traceId);
            tokenConsumer.accept(fallbackResponse());
            return;
        }

        if (inferenceQueue.isSaturated()) {
            throw new OllamaOverloadException("Inference queue saturated for streaming, traceId=" + traceId);
        }

        long startMs = System.currentTimeMillis();
        try {
            inferenceQueue.submit(() -> {
                callOllama(systemPrompt, userMessage, traceId, true, tokenConsumer);
                return null;
            }).get();
            long durationMs = System.currentTimeMillis() - startMs;
            metrics.recordInferenceLatency(ModelProvider.OLLAMA, durationMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OLLAMA, false, null);
        } catch (Exception e) {
            handleFailure(e, traceId);
            tokenConsumer.accept(fallbackResponse());
        }
    }

    private String callOllama(String systemPrompt, String userMessage, String traceId,
                               boolean stream, Consumer<String> tokenConsumer) throws Exception {
        String modelName = activeModel();
        String endpoint  = modelRegistry.getLlm().endpointUrl() + CHAT_PATH;

        Map<String, Object> payload = Map.of(
                "model", modelName,
                "stream", stream,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userMessage)
                )
        );

        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder().url(endpoint).post(body).build();

        if (stream && tokenConsumer != null) {
            return executeStreaming(request, traceId, tokenConsumer);
        } else {
            return executeBlocking(request, traceId);
        }
    }

    private String executeBlocking(Request request, String traceId) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new OllamaCallException("Ollama returned HTTP " + response.code() + " traceId=" + traceId);
            }
            ResponseBody respBody = response.body();
            if (respBody == null) return fallbackResponse();

            JsonNode root    = objectMapper.readTree(respBody.string());
            JsonNode message = root.path("message");
            String content   = message.path("content").asText("");

            int totalTokens = root.path("eval_count").asInt(0);
            long durationNs = root.path("eval_duration").asLong(0L);
            if (durationNs > 0) {
                long tokensPerSec = (long) (totalTokens / (durationNs / 1_000_000_000.0));
                metrics.updateTokenRate(ModelProvider.OLLAMA, tokensPerSec);
            }

            log.debug("OllamaRuntimeAdapter: blocking generation complete traceId={} tokens={}", traceId, totalTokens);
            return content;
        }
    }

    private String executeStreaming(Request request, String traceId, Consumer<String> tokenConsumer) throws Exception {
        Call call = httpClient.newCall(request);
        // register() aborts the call immediately if the client already went away while this
        // request sat in the single-threaded inference queue.
        cancellationManager.register(traceId, call::cancel);

        StringBuilder fullResponse = new StringBuilder();
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new OllamaCallException("Ollama streaming returned HTTP " + response.code());
            }
            ResponseBody respBody = response.body();
            if (respBody == null) return fallbackResponse();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(respBody.byteStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancellationManager.isCancelled(traceId)) {
                        log.info("OllamaRuntimeAdapter: stream cancelled traceId={}", traceId);
                        break;
                    }
                    if (line.isBlank()) continue;

                    JsonNode node = objectMapper.readTree(line);
                    String token = node.path("message").path("content").asText("");
                    if (!token.isEmpty()) {
                        tokenConsumer.accept(token);
                        fullResponse.append(token);
                    }

                    if (node.path("done").asBoolean(false)) break;
                }
            }
            return fullResponse.toString();

        } catch (IOException e) {
            // A cancellation aborts the OkHttp call mid-read, which surfaces as an IOException.
            // That is an expected, deliberate termination — NOT an Ollama failure. Rethrowing it
            // would mark the OLLAMA runtime degraded for every client that simply disconnected.
            if (call.isCanceled() || cancellationManager.isCancelled(traceId)) {
                log.info("OllamaRuntimeAdapter: stream aborted after cancellation traceId={}", traceId);
                return fullResponse.toString();
            }
            throw e;
        } finally {
            cancellationManager.deregister(traceId);
        }
    }

    private void handleFailure(Exception e, String traceId) {
        if (e.getCause() instanceof OutOfMemoryError) {
            metrics.recordOom(ModelProvider.OLLAMA);
            log.error("OllamaRuntimeAdapter: OOM during inference traceId={}", traceId);
        }
        metrics.recordTimeout(ModelProvider.OLLAMA);
        degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OLLAMA, true, e.getMessage());
        log.error("OllamaRuntimeAdapter: inference failed traceId={}: {}", traceId, e.getMessage(), e);
    }

    private String fallbackResponse() {
        return "Maaf, layanan AI sedang tidak tersedia. Silakan coba beberapa saat lagi.";
    }

    public static class OllamaCallException extends RuntimeException {
        public OllamaCallException(String msg) { super(msg); }
    }

    public static class OllamaOverloadException extends RuntimeException {
        public OllamaOverloadException(String msg) { super(msg); }
    }
}
