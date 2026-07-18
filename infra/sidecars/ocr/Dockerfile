FROM python:3.11-slim

WORKDIR /app
ENV PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/models \
    OCR_USE_GPU=false

# poppler-utils: PDF rasterisation (pdf2image). libgl1/libglib2.0-0: OpenCV runtime
# used by PaddleOCR. libgomp1: paddlepaddle CPU backend.
RUN apt-get update && apt-get install -y --no-install-recommends \
        poppler-utils libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# paddleocr pulls stringzilla, which ships no cp311 wheel and compiles from source, so a
# toolchain is required at install time. It is purged in the same layer: keeping gcc in the
# image would add weight and an attack surface that nothing at runtime needs.
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && pip install -r requirements.txt \
    && apt-get purge -y --auto-remove build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY storage.py app.py ./

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=300s --retries=5 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8081/health').status==200 else 1)"

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8081"]
