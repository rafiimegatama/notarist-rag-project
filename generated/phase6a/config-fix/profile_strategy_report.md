# PHASE 6A.3-FIX — Profile Strategy Report
**Project:** notarist-rag  
**Fix Date:** 2026-05-24  
**Priority:** P2

---

## Problem

Only one `application.yaml` existed. All four profiles (`local`, `dev`, `staging`, `prod`) resolved to the same configuration with environment variable substitution. Production deployments had no isolated override file to enforce stricter security settings.

---

## Profile Files Created

All files created at:
`backend-skeleton/notarist-web/src/main/resources/`

| File | Profile | Purpose |
|---|---|---|
| `application.yaml` | (base, all profiles) | Canonical namespace; no credential defaults; sensible non-secret defaults |
| `application-local.yaml` | `local` | docker-compose defaults; debug logging; relaxed actuator |
| `application-dev.yaml` | `dev` | Shared dev server; debug logging; no credential values |
| `application-staging.yaml` | `staging` | Production-like; restricted actuator; increased pool sizing |
| `application-prod.yaml` | `prod` | Maximum restriction; liveness/readiness only; production JWT TTL |

---

## Profile Activation

```yaml
# application.yaml (base)
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
```

Default profile is `local` (safe for developer machines).

| Deployment | `SPRING_PROFILES_ACTIVE` value |
|---|---|
| Developer laptop | `local` |
| Dev server | `dev` |
| Staging server | `staging` |
| Production | `prod` |

---

## Profile Override Matrix

| Property | base (`application.yaml`) | local | dev | staging | prod |
|---|---|---|---|---|---|
| MinIO access-key | `${MINIO_ACCESS_KEY}` (no default) | `minioadmin` | — | — | — |
| MinIO secret-key | `${MINIO_SECRET_KEY}` (no default) | `minioadmin` | — | — | — |
| Oracle pool-max | 20 | 5 | — | 15 | `${ORACLE_POOL_MAX:30}` |
| Oracle pool-min | 5 | 2 | — | 5 | `${ORACLE_POOL_MIN:10}` |
| JWT private-key-path | `${JWT_PRIVATE_KEY_PATH:file:./keys/...}` | explicit local | — | — | `${JWT_PRIVATE_KEY_PATH}` (no default) |
| JWT access TTL | 15 min | — | — | — | 10 min |
| JWT refresh TTL | 7 days | — | — | — | 3 days |
| Log level root | INFO | INFO | INFO | INFO | WARN |
| Log level com.notarist | INFO | DEBUG | DEBUG | INFO | INFO |
| Log level security | WARN | DEBUG | INFO | WARN | WARN |
| Actuator exposure | health,prometheus | health,metrics,prometheus,env,beans | health,metrics,prometheus | health,prometheus | health only |
| Health show-details | when-authorized | when-authorized | when-authorized | when-authorized | never |
| Queue workers | 3 | — | — | 5 | `${QUEUE_MAX_CONCURRENT_WORKERS:5}` |
| Ollama inference-timeout-ms | 60,000 | — | — | 60,000 | `${OLLAMA_INFERENCE_TIMEOUT_MS:60000}` |

`—` = inherits from base.

---

## Production Safety Rules Encoded in `application-prod.yaml`

1. `JWT_PRIVATE_KEY_PATH` has NO fallback — prod fails fast if key path not set
2. `JWT_PUBLIC_KEY_PATH` has NO fallback — same
3. JWT access TTL reduced to 10 min (vs 15 min default)
4. JWT refresh TTL reduced to 3 days (vs 7 days default)
5. Actuator exposes only `/actuator/health` — no prometheus, no metrics
6. `health.show-details: never` — no diagnostic detail in prod health response
7. Oracle pool min = 10 (vs 5 base) — warm connections for consistent latency
8. root log level = WARN — no INFO spam in production logs

---

## HikariCP Timeout Inconsistency Fixed

Phase 6A.3-FIX resolved the inconsistency across module configs:

| Module | Before | After |
|---|---|---|
| `AuthModuleConfig` | `connectionTimeout=20s, idleTimeout=300s, maxLifetime=1200s` | **Removed** — uses infra beans |
| `IngestModuleConfig` | `connectionTimeout=30s, idleTimeout=600s, maxLifetime=1800s` | **Removed** — uses infra beans |
| `SearchModuleConfig` | HikariCP via `DataSourceProperties` (no explicit timeouts) | **Removed** — uses infra beans |
| `PostgresConnectionConfig` (Phase 5) | `connectionTimeout=5s, idleTimeout=600s, maxLifetime=1800s` | **AUTHORITATIVE** — unchanged |

Single HikariCP pool for PostgreSQL, owned by `notarist-infra`.
