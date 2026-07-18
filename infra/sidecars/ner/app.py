"""
IndoBERT NER + PII redaction sidecar.

Contract (consumed by com.notarist.runtime.ner.IndoBertNerAdapter):
    POST /extract
        request : {"source_object_key": "...",        # OCR text object key
                   "document_type": "AKTA_JUAL_BELI",
                   "min_entity_confidence": 0.5,
                   "require_pii_redaction": true,
                   "model_variant": "indobert-base"}
        response: {"processed_object_key": "...",       # redacted text written back
                   "entities": {"PER": 3, "ORG": 1, "NIK": 2, ...},
                   "engine_used": "cahya/bert-base-indonesian-NER",
                   "pii_redacted": true,
                   "duration_ms": 1234}
    GET  /health -> {"status": "ok"}

Object-key contract: the sidecar downloads the OCR text from source_object_key,
extracts entities, redacts PII in place, and uploads the redacted text under a
derived key (notarist-ocr/... -> notarist-processed/..., suffix .ner.txt).

NerWorker refuses to advance the pipeline to chunking when pii_redacted is false,
so when require_pii_redaction is set we redact and report true.
"""
import os
import re
import time
import logging
from collections import Counter

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import (
    AutoTokenizer,
    AutoModelForTokenClassification,
    pipeline,
)

import storage

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ner")

MODEL_NAME = os.environ.get("NER_MODEL", "cahya/bert-base-indonesian-NER")
DEVICE = int(os.environ.get("NER_DEVICE", "-1"))  # -1 = CPU, 0 = first GPU
# Entity groups treated as PII and redacted from the text (person names by default).
PII_ENTITY_GROUPS = {
    g.strip().upper() for g in os.environ.get("NER_PII_GROUPS", "PER,PERSON").split(",") if g.strip()
}

# Indonesian identifiers that are PII regardless of the NER model's coverage.
_NIK_RE = re.compile(r"\b\d{16}\b")
_NPWP_RE = re.compile(r"\b\d{2}\.\d{3}\.\d{3}\.\d-\d{3}\.\d{3}\b")
_PHONE_RE = re.compile(r"\b(?:\+62|62|0)8[1-9]\d{6,10}\b")
_EMAIL_RE = re.compile(r"\b[\w.+-]+@[\w-]+\.[\w.-]+\b")

app = FastAPI(title="notarist-ner", version="1.0.0")

_nlp = None
_engine_name = MODEL_NAME


def nlp():
    global _nlp
    if _nlp is None:
        log.info("Loading NER model=%s device=%s", MODEL_NAME, DEVICE)
        tok = AutoTokenizer.from_pretrained(MODEL_NAME)
        mdl = AutoModelForTokenClassification.from_pretrained(MODEL_NAME)
        _nlp = pipeline(
            "token-classification",
            model=mdl,
            tokenizer=tok,
            aggregation_strategy="simple",
            device=DEVICE,
        )
        log.info("NER model loaded")
    return _nlp


@app.on_event("startup")
def _warmup():
    try:
        nlp()("Notaris menandatangani akta.")
    except Exception:  # noqa: BLE001
        log.exception("NER warmup failed (model will load lazily on first request)")


class ExtractRequest(BaseModel):
    source_object_key: str
    document_type: str = "UNKNOWN"
    min_entity_confidence: float = 0.5
    require_pii_redaction: bool = True
    model_variant: str = "indobert-base"


class ExtractResponse(BaseModel):
    processed_object_key: str
    entities: dict[str, int] = Field(default_factory=dict)
    engine_used: str
    pii_redacted: bool
    duration_ms: int


def _processed_key(source_key: str) -> str:
    key = source_key
    if "notarist-ocr/" in key:
        key = key.replace("notarist-ocr/", "notarist-processed/", 1)
    elif "notarist-raw/" in key:
        key = key.replace("notarist-raw/", "notarist-processed/", 1)
    else:
        key = "notarist-processed/" + key
    if key.endswith(".ocr.txt"):
        key = key[: -len(".ocr.txt")] + ".ner.txt"
    else:
        key = key + ".ner.txt"
    return key


def _merge_spans(spans: list[tuple[int, int]]) -> list[tuple[int, int]]:
    """Collapse overlapping/adjacent spans into disjoint ones.

    Required for correctness, not tidiness. The model spans and the regex spans are collected
    independently and DO overlap: a PER prediction can sit inside the e-mail the EMAIL regex
    also matched. Splicing them one by one — even right-to-left — is unsound, because replacing
    an inner span shifts every later offset, leaving the outer span's `end` pointing at stale
    text. Observed live: siti.rahayu@example.com redacted to "[REDACTED]le.com".

    That case over-redacted, which is survivable. The same arithmetic can just as easily cut
    short and leave PII in the output, which is not — this is the redaction that gates the whole
    pipeline. Merging first makes the spans disjoint, so every offset stays valid.
    """
    if not spans:
        return []
    ordered = sorted(spans)
    merged = [list(ordered[0])]
    for start, end in ordered[1:]:
        if start <= merged[-1][1]:          # overlapping or touching
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return [(s, e) for s, e in merged]


def _redact_spans(text: str, spans: list[tuple[int, int]]) -> str:
    """Splice out character spans, right-to-left so earlier offsets stay valid."""
    for start, end in sorted(_merge_spans(spans), key=lambda s: s[0], reverse=True):
        text = text[:start] + "[REDACTED]" + text[end:]
    return text


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "loaded": _nlp is not None}


@app.post("/extract", response_model=ExtractResponse)
def extract(req: ExtractRequest) -> ExtractResponse:
    started = time.time()

    try:
        text = storage.download_text(req.source_object_key)
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=f"cannot read {req.source_object_key}: {e}")

    entities: Counter = Counter()
    redact_spans: list[tuple[int, int]] = []

    # Model-driven entity extraction.
    for ent in nlp()(text):
        if float(ent.get("score", 0.0)) < req.min_entity_confidence:
            continue
        group = str(ent.get("entity_group", "MISC")).upper()
        entities[group] += 1
        if req.require_pii_redaction and group in PII_ENTITY_GROUPS:
            redact_spans.append((int(ent["start"]), int(ent["end"])))

    # Regex-driven Indonesian identifiers — always PII.
    for label, rx in (("NIK", _NIK_RE), ("NPWP", _NPWP_RE), ("PHONE", _PHONE_RE), ("EMAIL", _EMAIL_RE)):
        for m in rx.finditer(text):
            entities[label] += 1
            if req.require_pii_redaction:
                redact_spans.append((m.start(), m.end()))

    redacted_text = _redact_spans(text, redact_spans) if req.require_pii_redaction else text
    pii_redacted = bool(req.require_pii_redaction)

    processed_key = _processed_key(req.source_object_key)
    try:
        storage.upload_text(processed_key, redacted_text)
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"cannot write {processed_key}: {e}")

    duration_ms = int((time.time() - started) * 1000)
    log.info(
        "extracted key=%s entities=%d redactions=%d pii_redacted=%s ms=%d",
        req.source_object_key, sum(entities.values()), len(redact_spans), pii_redacted, duration_ms,
    )
    return ExtractResponse(
        processed_object_key=processed_key,
        entities=dict(entities),
        engine_used=_engine_name,
        pii_redacted=pii_redacted,
        duration_ms=duration_ms,
    )
