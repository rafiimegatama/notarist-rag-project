# PHASE 6A.3 — Secret Exposure Audit
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Classification:** SECURITY_RISK

---

## Audit Scope

Scanned: `application.yaml`, `docker-compose.yml`, `docker-compose.override.yml`, all `*Config.java`, `*Properties.java`, `*ModuleConfig.java`.

---

## CRITICAL — Inline Default Credentials in Source Code

### Finding SEC-01: MinIO Default Credentials in `IngestModuleConfig.java`

**File:** `phase2-ingest/notarist-ingest/.../IngestModuleConfig.java:22-24`

```java
@Value("${notarist.minio.access-key:minioadmin}") String accessKey,
@Value("${notarist.minio.secret-key:minioadmin}") String secretKey
```

**Risk:** The default values `minioadmin` / `minioadmin` are committed to source code. If the environment variable is not set, MinIO is accessed with the default credentials. A developer who clones the repo and runs without a `.env` file will have a working (insecure) configuration silently.

**Classification:** CRITICAL — credential literal in source code.

**Remediation:** Remove defaults from `@Value`. Use `@Value("${notarist.minio.access-key}")` (no default). Application will fail fast at startup rather than silently use insecure credentials.

---

### Finding SEC-02: PostgreSQL Password Default in `IngestModuleConfig.java`

**File:** `phase2-ingest/notarist-ingest/.../IngestModuleConfig.java:35`

```java
@Value("${notarist.postgres.password:notarist}") String password
```

**Risk:** The password default `notarist` (username == password) is committed to source code. Same silent-insecure risk as SEC-01.

**Classification:** CRITICAL — credential literal in source code.

**Remediation:** Remove default. Use `@Value("${notarist.postgres.password}")`.

---

## HIGH — Weak Defaults in application.yaml

### Finding SEC-03: MinIO Credentials Have Known Defaults

**File:** `application.yaml:61-62`

```yaml
access-key: ${MINIO_ACCESS_KEY:minioadmin}
secret-key: ${MINIO_SECRET_KEY:minioadmin}
```

**Risk:** `minioadmin` / `minioadmin` is the published MinIO default, documented publicly. Any deployment where `MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` are not set (e.g., dev server, staging without proper env setup) uses these credentials. An attacker with network access to MinIO port 9000 can authenticate.

**Classification:** HIGH — known default credential in YAML defaults.

**Remediation:**

```yaml
access-key: ${MINIO_ACCESS_KEY}     # no default — fail fast
secret-key: ${MINIO_SECRET_KEY}     # no default — fail fast
```

For `local` profile, use a `application-local.yaml` (git-ignored) with development credentials, never the base YAML.

---

### Finding SEC-04: Docker Compose MinIO Password Default

**File:** `docker-compose.yml:21`

```yaml
MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-minioadmin123}
```

**Risk:** If `.env` file is not present, `docker compose up` starts MinIO with password `minioadmin123`. This file is committed to source control. If the compose file is used in staging without a proper `.env` file, the default credential is active.

**Note:** Docker compose files with hardcoded defaults are broadly acceptable for development (see compose override pattern), but the risk is non-zero if this compose file is deployed directly to staging.

**Classification:** HIGH — default credential in committed compose file.

**Remediation:** Document in `README`: "Set `MINIO_ROOT_PASSWORD` in `.env` before running in any environment above local development." Add a compose file check or startup assertion.

---

### Finding SEC-05: PostgreSQL Default Password in Docker Compose

**File:** `docker-compose.yml:45`

```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-notarist123}
```

**Classification:** HIGH — default credential in committed compose file.

**Remediation:** Same as SEC-04. Require `.env` file.

---

## MEDIUM — Username Defaults Exposed

### Finding SEC-06: Oracle Username Default

**File:** `application.yaml:43`

```yaml
username: ${ORACLE_USER:notarist_app}
```

**Risk:** The Oracle application username `notarist_app` is visible in source. While username alone is not exploitable, it gives an attacker a valid username to target.

**Classification:** MEDIUM — username literal in YAML defaults.

---

### Finding SEC-07: Qdrant API Key Empty Default

**File:** `application.yaml:56`

```yaml
api-key: ${QDRANT_API_KEY:}
```

**Risk:** Empty default = unauthenticated Qdrant access. If `QDRANT_API_KEY` is not set, Qdrant is accessed without authentication. Qdrant's API key protection is optional but is the only access control mechanism for the vector store.

**Classification:** MEDIUM — missing authentication default.

**Remediation:** In production/staging profiles, require `QDRANT_API_KEY` to be non-empty. Add validation in `QdrantProperties` compact constructor: `if (apiKey == null || apiKey.isBlank()) log.warn("QdrantProperties: apiKey not set — vector store unauthenticated")`.

---

## No Issues Found (Positive Findings)

| Area | Status |
|---|---|
| `APP_ENCRYPTION_KEY` | No default — fails fast if not set ✓ |
| `APP_ENCRYPTION_SALT` | No default — fails fast if not set ✓ |
| `ORACLE_PASSWORD` | No default — fails fast if not set ✓ |
| `POSTGRES_PASSWORD` (app.yaml) | No default — fails fast if not set ✓ |
| JWT private/public key paths | Uses file references, not inline key material ✓ |
| No JWT secret inline in YAML | Correct — RSA key pair from files, not HMAC secret ✓ |
| No Ollama API key exposed | Ollama local-only, no auth required ✓ |
| `MINIO_UPLOAD_URL_TTL_MINUTES:15` | Presigned URL TTL is reasonable ✓ |

---

## Actuator Security Risk

**Finding SEC-08:** `SecurityConfig.java:25`

```java
.requestMatchers("/actuator/health/**", "/actuator/metrics").permitAll()
```

`management.endpoints.web.exposure.include: health,metrics,prometheus`

`/actuator/metrics` is publicly accessible. Prometheus metrics can reveal:
- Active thread counts and pool saturation (queue metrics)
- Inference latency histograms (model performance data)
- Circuit breaker states (which integrations are failing)
- JVM heap usage and GC behavior

For an enterprise legal platform, operational metrics should not be publicly accessible.

**Classification:** MEDIUM — operational data exposure.

**Remediation:**

```java
.requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
// Remove /actuator/metrics and /actuator/prometheus from public access
// Expose only via internal network or with HTTP Basic auth
```

---

## Summary

| Finding | Classification | Priority | Action |
|---|---|---|---|
| SEC-01: MinIO default credentials in source code | CRITICAL | P0 | Remove `@Value` defaults |
| SEC-02: PostgreSQL password default in source code | CRITICAL | P0 | Remove `@Value` defaults |
| SEC-03: MinIO weak defaults in application.yaml | HIGH | P1 | Remove YAML defaults, use profile-specific |
| SEC-04: Docker compose MinIO password default | HIGH | P1 | Document `.env` requirement |
| SEC-05: Docker compose PostgreSQL password default | HIGH | P1 | Document `.env` requirement |
| SEC-06: Oracle username exposed in YAML | MEDIUM | P2 | Acceptable for app username |
| SEC-07: Qdrant API key empty default | MEDIUM | P1 | Add validation warning |
| SEC-08: Actuator metrics publicly accessible | MEDIUM | P1 | Restrict to internal network |
