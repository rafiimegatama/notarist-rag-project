# PHASE 6A.3 — Profile Activation Audit
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Classification:** DEPLOYMENT_RISK / SECURITY_RISK

---

## Profile Inventory

### Profiles Declared

```yaml
# application.yaml:4-5
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
```

Default profile: `local`

### Profile-Specific Files Found

| File | Status |
|---|---|
| `application.yaml` | EXISTS — base configuration |
| `application-local.yaml` | **MISSING** |
| `application-dev.yaml` | **MISSING** |
| `application-staging.yaml` | **MISSING** |
| `application-prod.yaml` | **MISSING** |

**Finding:** Only one configuration file exists. There are ZERO profile-specific override files. All 4 profiles (`local`, `dev`, `staging`, `prod`) fall back to the same base configuration with environment variable substitution.

---

## Profile Validation: `local`

**Required:** Development convenience; debug logging; weak security acceptable; local service URLs

| Property | Value | Assessment |
|---|---|---|
| `spring.profiles.active` default | `local` | CORRECT — falls back to local |
| `ORACLE_URL` default | `jdbc:oracle:thin:@localhost:1521/NOTARISTDB` | ACCEPTABLE for local |
| `MINIO_ENDPOINT` default | `http://localhost:9000` | ACCEPTABLE for local |
| `LOG_LEVEL` default | `INFO` | LATENT — local often needs DEBUG |
| MinIO credentials | `minioadmin/minioadmin` | SECURITY_RISK — weak but local |
| JWT private key default | `file:./keys/notarist-private.pem` | RISK if key file absent → startup failure |
| `APP_ENCRYPTION_KEY` | No default → startup failure | RISK — developer must set this even for local |

**Missing:** A `application-local.yaml` file that:
- Sets `LOG_LEVEL=DEBUG` for `com.notarist`
- Sets relaxed security (disable actuator auth)
- Documents required local env vars in comments

---

## Profile Validation: `dev`

**Required:** Shared dev server; somewhat realistic config; auto-migration allowed

| Check | Assessment |
|---|---|
| Profile file exists | **MISSING** |
| Database URL override | No dev DB URL defined |
| Logging override | No dev-specific logging level |
| Feature flag override | None defined |
| Debug endpoints | No `/actuator/env` etc. safe to expose in dev |

**Missing profile file risk:** If `SPRING_PROFILES_ACTIVE=dev` is set on the dev server, Spring loads only `application.yaml`. All dev-specific overrides (different DB URLs, debug logging, relaxed timeouts) must be set via environment variables. No documentation of required vars exists.

---

## Profile Validation: `staging`

**Required:** Production-like config; no debug endpoints; TLS; real credentials from vault/secrets manager

| Check | Assessment |
|---|---|
| Profile file exists | **MISSING** |
| Prometheus exposure | `PROMETHEUS_ENABLED:true` default applies | DEPLOYMENT_RISK |
| Actuator exposure | `health,metrics,prometheus` all exposed | DEPLOYMENT_RISK |
| MinIO credentials | Defaults apply if env vars not set | SECURITY_RISK |
| Oracle pool sizing | Defaults: max=10 | May be too small for staging load |
| HikariCP idle timeout | Auth module: 300s; Infra: 600s — mismatch | CONFIG_RISK |

---

## Profile Validation: `prod`

**Required:** No defaults for credentials; TLS mandatory; minimal actuator exposure; production pool sizing; audit logging

| Check | Value/Status | Risk Level |
|---|---|---|
| Credential defaults | `minioadmin`, `notarist`, `notarist_app` exist | CRITICAL |
| Profile file | MISSING | CRITICAL |
| Actuator exposure | `metrics` and `prometheus` publicly accessible | HIGH |
| `show-details: when-authorized` | CORRECT for health | OK |
| JWT key path default | `./keys/notarist-private.pem` | HIGH — file must exist |
| Prometheus enabled default | `true` | MEDIUM — appropriate in prod only via internal network |
| Oracle pool max | `${ORACLE_POOL_MAX:10}` | May be insufficient for production load |
| Oracle pool min | `${ORACLE_POOL_MIN:2}` | Low — cold start latency risk |
| `QUEUE_LOCK_TIMEOUT_MS:300000` | 5-minute lock | ACCEPTABLE |
| No Liquibase/Flyway auto-config | Manual migration required | DEPLOYMENT_RISK |

---

## Unsafe Default Analysis

The following properties have defaults in `application.yaml` that are **unsafe for production**. They must be overridden by environment variables in every non-local deployment.

| Property | Default Value | Environment | Unsafe If Not Set |
|---|---|---|---|
| `MINIO_ACCESS_KEY` | `minioadmin` | staging, prod | YES — known public credential |
| `MINIO_SECRET_KEY` | `minioadmin` | staging, prod | YES — known public credential |
| `ORACLE_USER` | `notarist_app` | staging, prod | MEDIUM — username disclosed |
| `QDRANT_API_KEY` | `""` (empty) | staging, prod | YES — no auth on vector store |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | `15` | prod | ACCEPTABLE |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `7` | prod | ACCEPTABLE |
| `LOG_LEVEL` | `INFO` | prod | ACCEPTABLE |
| `QUEUE_MAX_CONCURRENT_WORKERS` | `3` | prod | May be too low for prod load |
| `ORACLE_POOL_MAX` | `10` | prod | May be too low for prod load |

---

## Missing Production Profile Overrides

The following configuration should be applied ONLY in production and should be in `application-prod.yaml`:

```yaml
# application-prod.yaml (MISSING — must be created)
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus   # remove metrics from public exposure
  endpoint:
    health:
      show-details: never           # hide health details even from authorized in prod
  metrics:
    export:
      prometheus:
        enabled: ${PROMETHEUS_ENABLED:true}

logging:
  level:
    root: WARN
    com.notarist: INFO
    org.springframework.security: WARN

notarist:
  security:
    jwt:
      private-key-path: ${JWT_PRIVATE_KEY_PATH}    # no default — must be explicitly set
      public-key-path: ${JWT_PUBLIC_KEY_PATH}       # no default — must be explicitly set
```

---

## HikariCP Pool Timeout Inconsistency Across Modules

| Module | Connection Timeout | Idle Timeout | Max Lifetime |
|---|---|---|---|
| `AuthModuleConfig` (Phase 1) | 20,000 ms | 300,000 ms (5 min) | 1,200,000 ms (20 min) |
| `IngestModuleConfig` (Phase 2) | 30,000 ms | 600,000 ms (10 min) | 1,800,000 ms (30 min) |
| `PostgresConnectionConfig` (Phase 5) | from `PostgresProperties` default: 5,000 ms | 600,000 ms | 1,800,000 ms |

Three different connection timeout values (5s, 20s, 30s) for what should be the same PostgreSQL pool. Phase 5 `PostgresConnectionConfig` (5s) is the most aggressive and risks "connection timeout before pool warms up" on cold start.

**Note:** This inconsistency also implies 3 separate HikariCP pools connecting to the same PostgreSQL server — wasteful and potentially exhausting PostgreSQL's `max_connections`.

---

## Summary

| Profile | File Exists | Credential Defaults Safe | Actuator Safe | Status |
|---|---|---|---|---|
| `local` | NO | NO (acceptable) | NO (acceptable) | MISSING FILE |
| `dev` | NO | NO | NO | MISSING FILE |
| `staging` | NO | NO | NO | **DEPLOYMENT_RISK** |
| `prod` | NO | NO | NO | **CRITICAL RISK** |

**Action Required:** Create `application-local.yaml`, `application-dev.yaml`, `application-staging.yaml`, `application-prod.yaml` with appropriate overrides for each environment.
