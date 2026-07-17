"""
Dense embedding sidecar — bge-m3 (1024-dim).

Contract (consumed by com.notarist.runtime.embedding.EmbeddingRuntimeWorker,
active when EMBED_PROVIDER=sidecar):
    POST /embed
        request : {"texts": ["...", ...]}
        response: {"embeddings": [[float x1024], ...], "dimension": 1024}
    GET  /health -> {"status": "ok"}

The Java side hard-asserts dimension == 1024 (QdrantVectorPayload.REQUIRED_DIMENSION)
and embeddings length == texts length, so both are enforced here too.
"""
import os
import time
import logging

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from FlagEmbedding import BGEM3FlagModel

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("embedding")

MODEL_NAME = os.environ.get("EMBED_MODEL", "BAAI/bge-m3")
REQUIRED_DIM = 1024
USE_FP16 = os.environ.get("EMBED_FP16", "false").lower() == "true"  # fp16 needs a GPU
MAX_LENGTH = int(os.environ.get("EMBED_MAX_LENGTH", "8192"))
BATCH_SIZE = int(os.environ.get("EMBED_BATCH_SIZE", "12"))

app = FastAPI(title="notarist-embedding", version="1.0.0")

_model: BGEM3FlagModel | None = None


def model() -> BGEM3FlagModel:
    global _model
    if _model is None:
        log.info("Loading bge-m3 model=%s fp16=%s", MODEL_NAME, USE_FP16)
        _model = BGEM3FlagModel(MODEL_NAME, use_fp16=USE_FP16)
        log.info("bge-m3 loaded")
    return _model


@app.on_event("startup")
def _warmup():
    try:
        model().encode(["warmup"], batch_size=1, max_length=64)
    except Exception:  # noqa: BLE001
        log.exception("Embedding warmup failed (model will load lazily on first request)")


class EmbedRequest(BaseModel):
    texts: list[str] = Field(default_factory=list)


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]
    dimension: int


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "dimension": REQUIRED_DIM, "loaded": _model is not None}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    if not req.texts:
        return EmbedResponse(embeddings=[], dimension=REQUIRED_DIM)

    started = time.time()
    dense = model().encode(
        req.texts,
        batch_size=BATCH_SIZE,
        max_length=MAX_LENGTH,
    )["dense_vecs"]

    embeddings = [[float(x) for x in vec] for vec in dense]

    for i, vec in enumerate(embeddings):
        if len(vec) != REQUIRED_DIM:
            raise HTTPException(
                status_code=500,
                detail=f"bge-m3 produced dimension {len(vec)} for text[{i}], expected {REQUIRED_DIM}",
            )

    log.info(
        "embedded texts=%d dim=%d ms=%d",
        len(req.texts), REQUIRED_DIM, int((time.time() - started) * 1000),
    )
    return EmbedResponse(embeddings=embeddings, dimension=REQUIRED_DIM)
