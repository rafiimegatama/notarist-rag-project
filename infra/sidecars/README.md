# Notarist ML Sidecars (Sprint 7)

The four ML services the Java runtime adapters call over HTTP. They were previously **missing** —
the Java adapters existed and were wired, but there was no service to call. These are real FastAPI
services with real model backends, conforming exactly to the contracts the adapters already speak.

| Service | Port | Endpoint(s) | Java adapter | Backend env var |
|---|---|---|---|---|
| **ocr** | 8081 | `POST /predict/ocr_system`, `/predict/ocr_system/batch`, `GET /health` | `PaddleOcrProvider` | `OCR_ENDPOINT` / `OCR_BASE_URL` |
| **ner** | 8082 | `POST /extract`, `GET /health` | `IndoBertNerAdapter` | `NER_BASE_URL` |
| **reranker** | 8083 | `POST /rerank`, `GET /health` | `RerankerRuntimeWorker` | `RERANKER_BASE_URL` |
| **embedding** | 8084 | `POST /embed`, `GET /health` | `EmbeddingRuntimeWorker` (sidecar mode) | `EMBEDDING_BASE_URL` |

Models: PaddleOCR (`paddleocr` PP-OCR), IndoBERT NER (`cahya/bert-base-indonesian-NER`),
cross-encoder (`BAAI/bge-reranker-v2-m3`), dense embeddings (`BAAI/bge-m3`, 1024-dim).

## Wire contracts (verbatim, matched to the adapters)

**OCR** — `POST /predict/ocr_system`
```jsonc
// request
{"source_object_key": "notarist-raw/{tenant}/{docId}", "language": "id",
 "enable_tables": true, "min_confidence": 0.5, "batch_size": 8}
// response
{"ocr_object_key": "notarist-ocr/{tenant}/{docId}.ocr.txt", "page_count": 3,
 "text_length": 5120, "confidence_avg": 0.94, "warnings": [], "duration_ms": 4210}
```

**NER** — `POST /extract`
```jsonc
// request
{"source_object_key": "notarist-ocr/.../{docId}.ocr.txt", "document_type": "AKTA_JUAL_BELI",
 "min_entity_confidence": 0.5, "require_pii_redaction": true, "model_variant": "indobert-base"}
// response
{"processed_object_key": "notarist-processed/.../{docId}.ner.txt",
 "entities": {"PER": 3, "ORG": 1, "NIK": 2}, "engine_used": "cahya/bert-base-indonesian-NER",
 "pii_redacted": true, "duration_ms": 1234}
```

**Reranker** — `POST /rerank`
```jsonc
// request
{"query": "...", "passages": ["...", "..."], "top_k": 5}
// response — original passage indices, sorted by score desc, truncated to top_k
{"results": [{"index": 2, "score": 0.97}, {"index": 0, "score": 0.61}]}
```

**Embedding** — `POST /embed`
```jsonc
// request
{"texts": ["...", "..."]}
// response — dimension is asserted == 1024 on both sides
{"embeddings": [[/* 1024 floats */], ...], "dimension": 1024}
```

## Object storage (OCR + NER only)

OCR and NER follow the **object-key reference contract**: the Java pipeline passes a storage key,
the sidecar fetches the object from GCS itself and writes its output back under a derived key. This
keeps multi-hundred-MB akta PDFs out of the JVM heap. Key flow:

```
notarist-raw/{tenant}/{docId}                 (upload)
  → OCR  → notarist-ocr/{tenant}/{docId}.ocr.txt
  → NER  → notarist-processed/{tenant}/{docId}.ner.txt   (PII redacted)
```

`storage.py` supports real GCS (ADC or `GOOGLE_APPLICATION_CREDENTIALS`) and an emulator via
`STORAGE_EMULATOR_HOST`. Reranker and embedding are pure compute — no storage.

## Run

```bash
# From infra/docker/. Real GCS:
export GCS_BUCKET=your-dev-bucket
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json   # or use ADC
docker compose build ocr ner reranker embedding
docker compose up -d postgres qdrant ollama ocr ner reranker embedding

# Offline object storage (no real GCS): opt into the emulator
docker compose --profile local-storage up -d fake-gcs
export STORAGE_EMULATOR_HOST=http://localhost:4443
# ...then pre-create the bucket named by GCS_BUCKET in the emulator.
```

Point the backend at the sidecars (defaults already match for a locally-run backend):
```bash
export OCR_ENDPOINT=http://localhost:8081
export NER_BASE_URL=http://localhost:8082
export RERANKER_BASE_URL=http://localhost:8083   # + RERANK_PROVIDER=crossencoder to use it
export EMBEDDING_BASE_URL=http://localhost:8084   # + EMBED_PROVIDER=sidecar to use it
```

First boot downloads model weights (slow — the `start-period` on each healthcheck is 300s). Weights
cache to the `*-models` named volumes, so subsequent boots are fast. Set `*_USE_GPU`/`*_DEVICE`/
`*_FP16` and add the compose `deploy.resources` GPU reservation for GPU acceleration.

## Verification status — READ THIS

- **Verified here:** every service's Python compiles (`py_compile`); both compose files are valid
  YAML; the request/response field names and endpoint paths were matched line-by-line against the
  Java adapters (`PaddleOcrProvider`, `IndoBertNerAdapter`, `RerankerRuntimeWorker`,
  `EmbeddingRuntimeWorker`) and `ModelRegistry`/`OcrProperties` endpoint resolution.
- **NOT verified here:** no Docker and no GPU were available in the build environment, so the images
  were **not built** and the models were **not downloaded or run**. The language-model behaviour
  (PaddleOCR on a real akta PDF, IndoBERT entity spans, bge-reranker/bge-m3 outputs) has not been
  executed. Treat the first `docker compose build` + one real document through the pipeline as the
  acceptance gate — that is the live E2E that Sprint 6 could not run and Sprint 7 enables.

## Known follow-ups

- **Full local E2E needs the backend pointed at the same object store.** With `STORAGE_EMULATOR_HOST`
  the sidecars use fake-gcs-server, but the backend's `GcsClientConfig` does not yet honour the
  emulator endpoint — either run against a real dev bucket, or add emulator support to the Java GCS
  client (small, isolated change) for a fully offline run.
- **Language mapping:** PaddleOCR keys Indonesian under `latin` (script-based); `app.py` maps
  `id → latin`. Swap in a fine-tuned Indonesian recognition model if accuracy needs it.
- **PII policy:** NER redacts person-name entity groups plus regex NIK/NPWP/phone/email. Extend
  `NER_PII_GROUPS` / the regex set to match the office's data-classification rules.
