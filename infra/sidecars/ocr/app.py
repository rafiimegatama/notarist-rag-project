"""
PaddleOCR sidecar.

Contract (consumed by com.notarist.runtime.ocr.provider.paddle.PaddleOcrProvider):
    POST /predict/ocr_system
        request : {"source_object_key": "notarist-raw/{tenant}/{docId}",
                   "language": "id",
                   "enable_tables": true,
                   "min_confidence": 0.5,
                   "batch_size": 8}
        response: {"ocr_object_key": "notarist-ocr/.../{docId}.ocr.txt",
                   "page_count": 3,
                   "text_length": 5120,
                   "confidence_avg": 0.94,   # 0.0..1.0 (Java asserts this range)
                   "warnings": [],
                   "duration_ms": 4210}
    POST /predict/ocr_system/batch
        request : {"source_object_keys": [...], "language": ..., ...}
        response: {"results": [ <same shape as above>, ... ]}   # aligned 1:1 with input
    GET  /health -> {"status": "ok"}

Object-key contract: the sidecar downloads the source document from object storage,
OCRs it, and writes the extracted text back under a derived key
(notarist-raw/... -> notarist-ocr/..., suffix .ocr.txt).
"""
import io
import os
import time
import logging

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from PIL import Image
from paddleocr import PaddleOCR

import storage

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ocr")

USE_GPU = os.environ.get("OCR_USE_GPU", "false").lower() == "true"
PDF_DPI = int(os.environ.get("OCR_PDF_DPI", "200"))
# PaddleOCR's stock recognition model for the Latin script covers Indonesian; the
# backend advertises {"id","en","ch"} but PaddleOCR keys Indonesian under "latin".
_LANG_MAP = {"id": "latin", "en": "en", "ch": "ch", "latin": "latin"}

app = FastAPI(title="notarist-ocr", version="1.0.0")

# One PaddleOCR instance per language (each carries its own recognition model).
_engines: dict[str, PaddleOCR] = {}


def engine(language: str) -> PaddleOCR:
    lang = _LANG_MAP.get((language or "id").lower(), "latin")
    if lang not in _engines:
        log.info("Loading PaddleOCR lang=%s gpu=%s", lang, USE_GPU)
        _engines[lang] = PaddleOCR(use_angle_cls=True, lang=lang, use_gpu=USE_GPU, show_log=False)
        log.info("PaddleOCR lang=%s loaded", lang)
    return _engines[lang]


@app.on_event("startup")
def _warmup():
    try:
        engine("id")
    except Exception:  # noqa: BLE001
        log.exception("OCR warmup failed (engine will load lazily on first request)")


class OcrRequest(BaseModel):
    source_object_key: str
    language: str = "id"
    enable_tables: bool = True
    min_confidence: float = 0.5
    batch_size: int = 1


class OcrBatchRequest(BaseModel):
    source_object_keys: list[str] = Field(default_factory=list)
    language: str = "id"
    enable_tables: bool = True
    min_confidence: float = 0.5
    batch_size: int = 1


class OcrResult(BaseModel):
    ocr_object_key: str
    page_count: int
    text_length: int
    confidence_avg: float
    warnings: list[str] = Field(default_factory=list)
    duration_ms: int


class OcrBatchResponse(BaseModel):
    results: list[OcrResult]


def _ocr_key(source_key: str) -> str:
    key = source_key
    if "notarist-raw/" in key:
        key = key.replace("notarist-raw/", "notarist-ocr/", 1)
    else:
        key = "notarist-ocr/" + key
    return key + ".ocr.txt"


def _load_pages(raw: bytes) -> list[np.ndarray]:
    """Return one RGB numpy array per page. PDFs are rasterised; images pass through."""
    if raw[:5] == b"%PDF-":
        from pdf2image import convert_from_bytes  # imported lazily; needs poppler-utils
        images = convert_from_bytes(raw, dpi=PDF_DPI)
        return [np.array(img.convert("RGB")) for img in images]
    img = Image.open(io.BytesIO(raw)).convert("RGB")
    return [np.array(img)]


def _ocr_document(req_key: str, language: str, min_confidence: float) -> tuple[str, int, float, list[str]]:
    """OCR one document -> (text, page_count, confidence_avg, warnings)."""
    raw = storage.download_bytes(req_key)
    pages = _load_pages(raw)

    ocr = engine(language)
    page_texts: list[str] = []
    confidences: list[float] = []
    warnings: list[str] = []

    for page_idx, page_img in enumerate(pages):
        result = ocr.ocr(page_img, cls=True)
        # PaddleOCR returns [ [ [box, (text, conf)], ... ] ] for a single image; guard both shapes.
        lines = result[0] if result and isinstance(result[0], list) else result
        page_lines: list[str] = []
        for line in lines or []:
            if not line or len(line) < 2:
                continue
            text, conf = line[1][0], float(line[1][1])
            if conf < min_confidence:
                continue
            page_lines.append(text)
            confidences.append(conf)
        if not page_lines:
            warnings.append(f"page {page_idx + 1}: no text above min_confidence={min_confidence}")
        page_texts.append("\n".join(page_lines))

    full_text = "\n\n".join(page_texts).strip()
    confidence_avg = float(sum(confidences) / len(confidences)) if confidences else 0.0
    return full_text, len(pages), confidence_avg, warnings


def _process(req_key: str, language: str, min_confidence: float) -> OcrResult:
    started = time.time()
    try:
        text, page_count, confidence_avg, warnings = _ocr_document(req_key, language, min_confidence)
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=f"OCR failed for {req_key}: {e}")

    ocr_key = _ocr_key(req_key)
    try:
        storage.upload_text(ocr_key, text)
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"cannot write {ocr_key}: {e}")

    duration_ms = int((time.time() - started) * 1000)
    log.info(
        "ocr key=%s pages=%d chars=%d conf=%.3f ms=%d",
        req_key, page_count, len(text), confidence_avg, duration_ms,
    )
    return OcrResult(
        ocr_object_key=ocr_key,
        page_count=page_count,
        text_length=len(text),
        confidence_avg=confidence_avg,
        warnings=warnings,
        duration_ms=duration_ms,
    )


@app.get("/health")
def health():
    return {"status": "ok", "gpu": USE_GPU, "loaded_langs": list(_engines.keys())}


@app.post("/predict/ocr_system", response_model=OcrResult)
def ocr_system(req: OcrRequest) -> OcrResult:
    return _process(req.source_object_key, req.language, req.min_confidence)


@app.post("/predict/ocr_system/batch", response_model=OcrBatchResponse)
def ocr_system_batch(req: OcrBatchRequest) -> OcrBatchResponse:
    # Alignment is the batch contract: exactly one result per input key, in order.
    results = [_process(k, req.language, req.min_confidence) for k in req.source_object_keys]
    return OcrBatchResponse(results=results)
