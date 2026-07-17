"""
Cross-encoder reranker sidecar — bge-reranker-v2-m3.

Contract (consumed by com.notarist.runtime.reranker.RerankerRuntimeWorker):
    POST /rerank
        request : {"query": "...", "passages": ["...", ...], "top_k": N}
        response: {"results": [{"index": <int>, "score": <float 0..1>}, ...]}
    GET  /health -> {"status": "ok"}

The response returns ORIGINAL passage indices (the Java side maps index -> chunkId),
sorted by descending score and truncated to top_k. This is pure compute: no object
storage, no side effects.
"""
import os
import time
import logging

from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import CrossEncoder

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("reranker")

MODEL_NAME = os.environ.get("RERANK_MODEL", "BAAI/bge-reranker-v2-m3")
MAX_LENGTH = int(os.environ.get("RERANK_MAX_LENGTH", "512"))
DEVICE = os.environ.get("RERANK_DEVICE", "cpu")  # "cuda" when a GPU is available

app = FastAPI(title="notarist-reranker", version="1.0.0")

# Loaded once at startup. bge-reranker-v2-m3 has a single output label, so
# sentence-transformers applies a sigmoid by default and predict() returns 0..1.
_model: CrossEncoder | None = None


def model() -> CrossEncoder:
    global _model
    if _model is None:
        log.info("Loading cross-encoder model=%s device=%s", MODEL_NAME, DEVICE)
        _model = CrossEncoder(MODEL_NAME, max_length=MAX_LENGTH, device=DEVICE)
        log.info("Cross-encoder loaded")
    return _model


@app.on_event("startup")
def _warmup():
    try:
        model().predict([("warmup", "warmup passage")])
    except Exception:  # noqa: BLE001 — warmup must never block startup
        log.exception("Reranker warmup failed (model will load lazily on first request)")


class RerankRequest(BaseModel):
    query: str
    passages: list[str] = Field(default_factory=list)
    top_k: int = 5


class RerankItem(BaseModel):
    index: int
    score: float


class RerankResponse(BaseModel):
    results: list[RerankItem]


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "loaded": _model is not None}


@app.post("/rerank", response_model=RerankResponse)
def rerank(req: RerankRequest) -> RerankResponse:
    if not req.passages:
        return RerankResponse(results=[])

    started = time.time()
    scores = model().predict(
        [(req.query, p) for p in req.passages],
        convert_to_numpy=True,
        show_progress_bar=False,
    )

    ranked = sorted(
        ({"index": i, "score": float(s)} for i, s in enumerate(scores)),
        key=lambda r: r["score"],
        reverse=True,
    )
    top_k = req.top_k if req.top_k and req.top_k > 0 else len(ranked)
    results = [RerankItem(**r) for r in ranked[:top_k]]

    log.info(
        "reranked passages=%d top_k=%d ms=%d",
        len(req.passages), top_k, int((time.time() - started) * 1000),
    )
    return RerankResponse(results=results)
