# PHASE 6A.3-FIX — Secret Hygiene Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P0 (SEC-01, SEC-02) + P1

---

## Problem

Hardcoded credential literals existed in committed Java source files. Any developer cloning the repository would have a working (insecure) configuration without requiring any `.env` setup.

---

## Fixes Applied

### SEC-01 — MinIO Credentials Removed from `IngestModuleConfig`

**Before:**
```java
@Value("${notarist.minio.access-key:minioadmin}") String accessKey,
@Value("${notarist.minio.secret-key:minioadmin}") String secretKey
```

**After:**
```java
@Value("${notarist.infra.minio.access-key}") String accessKey,
@Value("${notarist.infra.minio.secret-key}") String secretKey
```

No default value. Application fails fast at startup if `MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` are not set.

---

### SEC-02 — PostgreSQL Password Removed from `IngestModuleConfig`

Entire ingest-owned PostgreSQL DataSource bean was removed (see namespace/bean deduplication fix). The `POSTGRES_PASSWORD` is now consumed exclusively by `PostgresProperties` which has no hardcoded default — it throws `IllegalStateException` if `POSTGRES_PASSWORD` is not set.

---

### SEC-03 — MinIO Weak Defaults Removed from `application.yaml`

**Before:**
```yaml
access-key: ${MINIO_ACCESS_KEY:minioadmin}
secret-key: ${MINIO_SECRET_KEY:minioadmin}
```

**After:**
```yaml
access-key: ${MINIO_ACCESS_KEY}
secret-key: ${MINIO_SECRET_KEY}
```

No fallback. If env vars not set → `IllegalStateException("spring.minio.access-key is required")` at startup (per `MinioProperties` compact constructor validation).

---

### SEC-Local — MinIO Defaults Moved to `application-local.yaml`

For local docker-compose development only, the MinIO defaults are explicitly declared in the local profile file:
```yaml
# application-local.yaml
notarist:
  infra:
    minio:
      access-key: minioadmin
      secret-key: minioadmin
```

**This file is never deployed to staging or production** (protected by profile activation: `SPRING_PROFILES_ACTIVE=local`).

---

## Secret Classification

| Secret | Env Var | Required? | Fallback | Risk if Missing |
|---|---|---|---|---|
| Oracle password | `ORACLE_PASSWORD` | REQUIRED | None | Startup failure (good) |
| PostgreSQL password | `POSTGRES_PASSWORD` | REQUIRED | None | Startup failure (good) |
| MinIO access key | `MINIO_ACCESS_KEY` | REQUIRED | None (local only) | Startup failure (good) |
| MinIO secret key | `MINIO_SECRET_KEY` | REQUIRED | None (local only) | Startup failure (good) |
| App encryption key | `APP_ENCRYPTION_KEY` | REQUIRED | None | Startup failure (good) |
| App encryption salt | `APP_ENCRYPTION_SALT` | REQUIRED | None | Startup failure (good) |
| JWT private key path | `JWT_PRIVATE_KEY_PATH` | REQUIRED_PROD | `./keys/notarist-private.pem` | File not found → startup failure |
| JWT public key path | `JWT_PUBLIC_KEY_PATH` | REQUIRED_PROD | `./keys/notarist-public.pem` | File not found → startup failure |
| Qdrant API key | `QDRANT_API_KEY` | OPTIONAL | `""` (no auth) | Unauthenticated vector store |
| Ollama base URL | `OLLAMA_BASE_URL` | OPTIONAL | `http://localhost:11434` | AI unavailable (degraded mode) |

---

## `.env.example` Created

File: `/generated/.env.example`

Contains all environment variables with placeholder values and requirement classification (`[REQUIRED]`, `[REQUIRED_PROD]`, `[OPTIONAL]`). Safe to commit — no real values.

**Usage:**
```bash
cp .env.example .env
# Edit .env with real values
# docker compose --env-file .env up
```

---

## Remaining Latent Risks (Not Fixed — Deferred)

| Item | Risk | Action |
|---|---|---|
| `docker-compose.yml` MinIO default password `minioadmin123` | HIGH — default in compose file | Document in README: require `.env` for non-local |
| `docker-compose.yml` PostgreSQL default password `notarist123` | HIGH — default in compose file | Same |
| Qdrant API key empty default | MEDIUM — no auth on vector store | Add warning log in `QdrantProperties` if blank in non-local profile |

---

## Files Modified

| File | Change |
|---|---|
| `phase2-ingest/.../IngestModuleConfig.java` | Removed `minioadmin` default; removed `notarist` PostgreSQL default; removed PostgreSQL beans |
| `backend-skeleton/.../application.yaml` | Removed `minioadmin` fallback for `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` |
| `backend-skeleton/.../application-local.yaml` | Added explicit local-only MinIO defaults |
| `generated/.env.example` | Created with all required/optional env vars documented |
