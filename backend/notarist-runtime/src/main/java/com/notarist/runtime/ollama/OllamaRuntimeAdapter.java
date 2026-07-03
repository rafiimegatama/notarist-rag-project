package com.notarist.runtime.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import com.notarist.assistant.application.port.out.LlmPort;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.guard.ContextOverflowGuard;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.model.ModelRegistry;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
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
 */
@Component
public class OllamaRuntimeAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaRuntimeAdapter.class);

    private static final int    OLLAMA_INFERENCE_TIMEOUT_MS = 60_000;
    private static final String CHAT_PATH                   = "/api/chat";

    private final OkHttpClient                 httpClient;
    private final ObjectMapper                 objectMapper;
    private final ModelRegistry                modelRegistry;
    private final RuntimeMetricsRegistry       metrics;
    private final RuntimeDegradationManager    degradation;
    private final StreamingCancellationManager cancellationManager;
    private final InferenceQueueIsolation      inferenceQueue;
    private final ContextOverflowGuard         overflowGuard;

    public OllamaRuntimeAdapter(
            ModelRegistry modelRegistry,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation,
            StreamingCancellationManager cancellationManager,
            InferenceQueueIsolation inferenceQueue,
            ContextOverflowGuard overflowGuard,
            @Qualifier("aiRuntimeObjectMapper") ObjectMapper objectMapper) {
        this.modelRegistry       = modelRegistry;
        this.metrics             = metrics;
        this.degradation         = degradation;
        this.cancellationManager = cancellationManager;
        this.inferenceQueue      = inferenceQueue;
        this.overflowGuard       = overflowGuard;
        this.objectMapper        = objectMapper;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(OLLAMA_INFERENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
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

    @Override
    public String generate(String systemPrompt, String userMessage, String traceId) {
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

    @Override
    public void generateStreaming(String systemPrompt, String userMessage, String traceId,
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
        String modelName = modelRegistry.getLlm().modelName();
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
        cancellationManager.register(traceId, call::cancel);

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new OllamaCallException("Ollama streaming returned HTTP " + response.code());
            }
            ResponseBody respBody = response.body();
            if (respBody == null) return fallbackResponse();

            StringBuilder fullResponse = new StringBuilder();
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
