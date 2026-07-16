"""
Object-storage access for the sidecars.

The OCR and NER services follow an object-key-reference contract: the Java pipeline
passes a storage key, the sidecar fetches the object itself and writes its output
back under a derived key. Single GCS bucket, prefix-namespaced (notarist-raw/,
notarist-ocr/, notarist-processed/ ...), matching the backend's GcsProperties.

Backends:
  - Real GCS via Application Default Credentials (Cloud Run runtime SA) or a
    service-account key (GOOGLE_APPLICATION_CREDENTIALS).
  - fake-gcs-server / any emulator via STORAGE_EMULATOR_HOST (anonymous creds).
"""
import functools
import os

from google.cloud import storage
from google.auth.credentials import AnonymousCredentials

_BUCKET = os.environ.get("GCS_BUCKET")
_PROJECT = os.environ.get("GCS_PROJECT") or os.environ.get("GOOGLE_CLOUD_PROJECT")


@functools.lru_cache(maxsize=1)
def _client() -> storage.Client:
    if os.environ.get("STORAGE_EMULATOR_HOST"):
        return storage.Client(
            project=_PROJECT or "notarist-local",
            credentials=AnonymousCredentials(),
        )
    return storage.Client(project=_PROJECT) if _PROJECT else storage.Client()


def _bucket():
    if not _BUCKET:
        raise RuntimeError("GCS_BUCKET env var is required for object-storage access")
    return _client().bucket(_BUCKET)


def download_bytes(key: str) -> bytes:
    return _bucket().blob(key).download_as_bytes()


def download_text(key: str, encoding: str = "utf-8") -> str:
    return _bucket().blob(key).download_as_bytes().decode(encoding)


def upload_text(key: str, text: str) -> str:
    _bucket().blob(key).upload_from_string(
        text.encode("utf-8"), content_type="text/plain; charset=utf-8"
    )
    return key


def exists(key: str) -> bool:
    return _bucket().blob(key).exists()
