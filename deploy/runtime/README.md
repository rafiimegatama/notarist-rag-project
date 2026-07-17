# Notarist — Enterprise AI Runtime Platform

Provider-agnostic AI runtime. Business logic depends only on application ports; a unified registry
selects concrete providers from configuration. Adding a backend (vLLM, OpenAI, Gemini, Anthropic,
OpenRouter, TensorRT-LLM) is one class — no registry, router, or business-logic change.

## 1. Runtime architecture

```
Spring Boot (AssistantOrchestrator, SemanticRetriever, RerankerService, EmbeddingWorker)
      │  depends ONLY on application ports (unchanged): LlmPort, QueryEmbeddingPort,
      │  EmbeddingPort, RerankerPort
      ▼
AI Runtime Layer — router beans (RegistryLlmPort, *EmbeddingRuntimeAdapter, RegistryRerankerPort)
      ▼
RuntimeRegistry            ← the one unified registry
      ├── LlmRegistry
      ├── EmbeddingRegistry
      ├── RerankerRegistry
      └── (future) OCRRegistry — already exists as OcrProviderRegistry; kept decoupled for now
      ▼
SPI interfaces:  InferenceProvider · EmbeddingProvider · RerankerProvider   (extend RuntimeProvider)
      ▼
Concrete providers:  ollama | (future) vllm · openai · gemini · anthropic · openrouter · tensorrt
```

Every registry exposes **only interfaces**. Nothing outside `com.notarist.runtime.provider`
instantiates a provider. Provider selection is resolved and validated **at startup** (a bad
`*_PROVIDER` id throws with the list of registered ids — never a green boot that dead-letters later).

## 2. Registry architecture

| Registry | Selects from | Config (env) | Providers (`id`) |
|---|---|---|---|
| `LlmRegistry` | `InferenceProvider` beans | `notarist.runtime.llm.provider` (`LLM_PROVIDER`) | `ollama` |
| `EmbeddingRegistry` | `EmbeddingProvider` beans | `notarist.runtime.embedding.provider` (`EMBED_PROVIDER`) | `ollama`, `sidecar` |
| `RerankerRegistry` | `RerankerProvider` beans | `notarist.runtime.reranker.provider` (`RERANK_PROVIDER`) | `none`, `crossencoder` |
| `RuntimeRegistry` | the three above | — | facade: `llm()`, `embedding()`, `reranker()` |

All three share `AbstractRuntimeRegistry`: id-indexing, duplicate detection, fail-fast selection,
non-throwing `healthOfAll()`. Same shape as the OCR module's `OcrProviderRegistry`.

## 3. Installed local models (RTX 5060 Ti 16 GB)

`./ollama-setup.sh` installs Ollama, applies the 16 GB tuning below, and pulls:

| Role | Model | Ollama tag | ~VRAM | Notes |
|---|---|---|---|---|
| **Chat + Reasoning** | Qwen3 14B | `qwen3:14b` | ~9 GB (Q4_K_M) | Native *thinking*; recommended default. |
| Chat (light/fallback) | Llama 3.1 8B | `llama3.1:8b-instruct-q8_0` | ~8.5 GB | `PULL_LIGHT=true`; more concurrency headroom. |
| Reasoning (alt) | Qwen3 8B / DeepSeek-R1 8B | `qwen3:8b` / `deepseek-r1:8b` | ~5–6 GB | Lighter reasoning. |
| **Embedding** | bge-m3 | `bge-m3` | ~1.2 GB | **1024-dim ✅** (matches the fixed index). |
| **Reranking** | bge-reranker-v2-m3 | (sidecar, not Ollama) | ~1.2 GB | Cross-encoder HTTP service; `RERANK_PROVIDER=crossencoder`. |
| **Vision (future)** | Qwen2.5-VL 7B / Llama-3.2-Vision 11B | `qwen2.5vl:7b` / `llama3.2-vision:11b` | ~6–9 GB | For a future vision provider; not wired yet. |

> Embedding must emit **1024 dims**. `nomic-embed-text` (768) would fail dimension validation — do
> not use it as-is.

## 4. GPU optimization (RTX 5060 Ti 16 GB · 32 GB DDR5 · Ryzen 5 7500F)

Applied by `ollama-setup.sh` as a systemd override:

| Setting | Value | Why |
|---|---|---|
| Flash Attention | `OLLAMA_FLASH_ATTENTION=1` | Lower KV memory + faster attention on Blackwell. |
| KV cache | `OLLAMA_KV_CACHE_TYPE=q8_0` | ~½ the VRAM of f16; lets a 14B Q4 hold 8–16k context in 16 GB. |
| Context window | `OLLAMA_CONTEXT_LENGTH=8192` | Safe GPU-resident default for 14B Q4; raise to 16384 only after checking VRAM headroom. |
| Parallel requests | `OLLAMA_NUM_PARALLEL=2` | KV splits across slots — 2 on 16 GB; 1 for max single-request context. |
| Model keep-alive | `OLLAMA_KEEP_ALIVE=30m` | Hot for 30 min idle, then unload; `-1` pins, `0` unloads immediately. |
| Loaded models | `OLLAMA_MAX_LOADED_MODELS=2` | Chat (~9 GB) + bge-m3 (~1.2 GB) co-resident; embed never evicts chat. |
| GPU scheduling | `OLLAMA_SCHED_SPREAD=0` | Single GPU — keep the whole model on one device. |
| GPU layers | default (all) | Do **not** lower `num_gpu`; full offload avoids CPU fallback. |
| Batch size | `num_batch=512` (default) | Fine for this GPU; no change needed. |
| CPU threads | `num_thread=6` (physical cores) | GPU does the work; match physical cores to avoid SMT contention. |

**Avoid CPU fallback:** Ollama spills layers to CPU (10–50× slower) only when model + KV exceeds
VRAM. Keep total loaded (chat + embed + KV) under ~15 GB (≈1 GB headroom): 14B Q4 + bge-m3 + 8k
q8_0 KV fits. If you raise context or model size and hit OOM, reduce context first, then model size.

## 5. Runtime configuration

Provider and **model** are separate axes (Phase 4) — changing a model is config-only:

```
LLM_PROVIDER=ollama        LLM_MODEL=qwen3:14b
EMBED_PROVIDER=ollama      EMBED_MODEL=bge-m3
RERANK_PROVIDER=none        # or: crossencoder + RERANK_MODEL=bge-reranker-v2-m3
OLLAMA_BASE_URL=http://localhost:11434
LLM_CONNECT_TIMEOUT_MS=5000  LLM_READ_TIMEOUT_MS=120000  LLM_WRITE_TIMEOUT_MS=10000
```

| Env | Property | Default |
|---|---|---|
| `LLM_PROVIDER` / `LLM_MODEL` | `notarist.runtime.llm.provider` / `.model` | `ollama` / `qwen3:14b` |
| `EMBED_PROVIDER` / `EMBED_MODEL` | `notarist.runtime.embedding.provider` / `.model` | `ollama` / `bge-m3` |
| `RERANK_PROVIDER` / `RERANK_MODEL` | `notarist.runtime.reranker.provider` / `.model` | `none` / `bge-reranker-v2-m3` |
| `LLM_READ_TIMEOUT_MS` | `notarist.runtime.llm.read-timeout-ms` | `120000` |

Blank `LLM_MODEL` falls back to the `ModelRegistry` default, so nothing breaks if unset.

## 6. Provider capability matrix

Each provider declares `ProviderCapabilities`; the runtime routes on declared capability, never on
provider name. Reported live at `/actuator/health → aiRuntime`.

| Provider | streaming | vision | toolCalling | jsonMode | embedding | reranking | thinking | batch |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| `ollama` (LLM) | ✅ | ✖ | ✅ | ✅ | ✖ | ✖ | ✅ | ✖ |
| `ollama` (embedding) | ✖ | ✖ | ✖ | ✖ | ✅ | ✖ | ✖ | ✅ (64) |
| `sidecar` (embedding) | ✖ | ✖ | ✖ | ✖ | ✅ | ✖ | ✖ | ✅ (32) |
| `crossencoder` (reranker) | ✖ | ✖ | ✖ | ✖ | ✖ | ✅ | ✖ | ✅ (64) |
| `none` (reranker) | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |

(vision on the Ollama LLM provider is model-dependent and left `false` until a vision provider is
wired — see below.)

## 7. Health (Phase 7)

`GET /actuator/health` → `aiRuntime` reports: current provider + model per kind, provider
availability, probe status (UP/DOWN/UNKNOWN), **streaming availability**, **embedding availability**,
per-provider **capabilities**, and **GPU** (CUDA, VRAM, cores) from `RuntimeCapabilityDetector`.
DOWN only when the active LLM provider is down. `./healthcheck.sh` prints this plus Ollama
`/api/ps` (loaded models). `./verify.sh` runs full end-to-end checks.

## 8. Streaming (Phase 8)

Preserved and capability-gated. `RegistryLlmPort.stream()` → active provider. Ollama streams NDJSON
token-by-token; **cancellation** via `StreamingCancellationManager` (a disconnected SSE client
aborts the in-flight OkHttp call, and a cancel that arrives while still queued is honoured);
**backpressure** via `InferenceQueueIsolation` (single-threaded executor + CallerRunsPolicy). All
LLM timeouts are config-driven.

## 9. Future provider guide (Phase 9)

Adding `GeminiProvider` / `OpenAIProvider` / `vLLMProvider` requires **only** a new class:

```java
@Component
public class OpenAIProvider implements InferenceProvider {
    public String id() { return "openai"; }                       // unique
    public String activeModel() { return configuredModel; }        // from notarist.runtime.llm.model
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.builder()
            .streaming(true).toolCalling(true).jsonMode(true).vision(true).build();
    }
    public LlmResponse invoke(LlmRequest r) { /* map to vendor SDK */ }
    public void stream(LlmRequest r, Consumer<LlmStreamChunk> c) { /* stream via same contract */ }
    public boolean isAvailable() { /* ping */ }
    public RuntimeProviderHealth health() { /* UP/DOWN/UNKNOWN */ }
}
```

Then `LLM_PROVIDER=openai`. **No** change to `RuntimeRegistry`, any router, any application port, or
any business logic — Spring discovers the bean, the registry indexes it by `id()`, config selects it.
Same pattern for `EmbeddingProvider` / `RerankerProvider`.
