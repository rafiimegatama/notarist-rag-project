package com.notarist.runtime.model;

/** AI runtime backend provider identifiers. */
public enum ModelProvider {

    /** Ollama — local LLM inference server (llama3.2, qwen2.5, etc.) */
    OLLAMA,

    /** bge-m3 — dense embedding model served via local HTTP endpoint */
    BGE_M3,

    /** PaddleOCR — OCR runtime served via PaddlePaddle serving */
    PADDLEOCR,

    /** Cross-encoder reranker (e.g., bge-reranker-v2-m3) via local HTTP endpoint */
    CROSS_ENCODER
}
