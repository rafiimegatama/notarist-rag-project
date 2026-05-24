# PHASE 6A.3 — OpenAPI Contract Consistency Report
**Project:** notarist-rag  
**Validation Date:** 2026-05-24  
**Classification:** LATENT_RISK / CONFIG_RISK

---

## Scope

Validates consistency between:
1. Request/Response DTOs in code vs expected API contract
2. Skeleton DTOs vs implementation DTOs (duplicate class drift)
3. SSE event schema consistency
4. `ApiResponse` wrapper consistency
5. Enum serialization consistency
6. OpenAPI config completeness

---

## Finding OA-01 — No Generated OpenAPI Spec File (CONFIG_RISK)

`OpenApiConfig.java` registers a programmatic `OpenAPI` bean with:
- Title: "NOTARIST RAG Platform API"
- Version: "1.0.0"
- JWT bearer auth scheme

**However:** No static `openapi.yaml` or `openapi.json` file exists in the repository. The Javadoc comment says: `TODO (STEP 8B): wire full API spec from /generated/openapi/notarist-api.yaml` — but no such file has been generated.

**Impact:** No machine-readable API contract exists. Client teams cannot validate against a spec. Springdoc will generate spec dynamically from annotations, but this has not been validated for completeness.

**Remediation (Phase 6A.4):** Export the Springdoc-generated spec to `/generated/openapi/notarist-api.yaml` and commit it. Use `springdoc-openapi-gradle-plugin` to generate it at build time.

---

## Finding OA-02 — DTO Schema Mismatch: AssistantRequest vs AssistantQueryRequest

**CRITICAL — Two incompatible request DTOs for the same endpoint**

| DTO | Location | Fields |
|---|---|---|
| `AssistantRequest` (implementation) | `phase4-assistant/.../api/request/AssistantRequest.java` | `rawQuery`, `maxClassificationLevel`, `documentTypeFilter`, `safetyMode`, `maxResults`, `contextTokenBudget` |
| `AssistantQueryRequest` (skeleton) | `backend-skeleton/notarist-assistant/.../AssistantQueryRequest.java` | `queryText`, `stream`, `topK`, `searchIntent` |

`AssistantController` imports and uses `AssistantRequest` from phase4. The skeleton `AssistantQueryRequest` is dead code that is never consumed by any controller.

**Field name divergence:**
- `rawQuery` (phase4) vs `queryText` (skeleton) — different names, same concept
- `maxResults` (phase4) vs `topK` (skeleton) — different names, same concept
- `safetyMode`, `maxClassificationLevel`, `documentTypeFilter`, `contextTokenBudget` — exist only in phase4, not in skeleton
- `stream`, `searchIntent` — exist only in skeleton, not in phase4

**Impact:** The skeleton contract is the documented API. Client teams using the skeleton's `AssistantQueryRequest` as reference will send `queryText` but the controller expects `rawQuery` → `null` query, silent failure.

**Remediation:** Reconcile into a single DTO. Phase4's `AssistantRequest` has the richer, more accurate schema. Update the skeleton to match, or delete the skeleton DTO and use the phase4 one as canonical.

---

## Finding OA-03 — SearchResponse DTO: Skeleton vs Implementation Divergence

| DTO | Location | Key Fields |
|---|---|---|
| `SearchResponse` (implementation) | `phase3-search/.../api/response/SearchResponse.java` | `queryId`, `status`, `intent`, `normalizedQuery`, `contextText`, `groundingLevel`, `groundingOverallScore`, `citations`, `retrievedChunkCount`, `estimatedTokenCount`, `contextTruncated`, `processingTimeMs`, `errorMessage` |
| `SearchResponse` (skeleton) | `backend-skeleton/notarist-search/.../api/response/SearchResponse.java` | Needs check |

Both exist as separate classes with the same simple name. The phase3 implementation is the active one.

---

## Finding OA-04 — IngestionJobStatusResponse Duplication

| DTO | Location |
|---|---|
| `IngestionStatusResponse` | `phase2-ingest/.../api/response/IngestionStatusResponse.java` |
| `IngestionJobStatusResponse` | `backend-skeleton/notarist-ingest/.../api/response/IngestionJobStatusResponse.java` |

Two different response DTOs for ingestion status, with different class names but likely overlapping content. Client teams may reference the skeleton (`IngestionJobStatusResponse`) while the controller returns `IngestionStatusResponse`.

---

## Finding OA-05 — UploadUrlResponse Duplication

| DTO | Location |
|---|---|
| `UploadUrlResponse` | `phase2-ingest/.../api/response/UploadUrlResponse.java` |
| `UploadUrlResponse` | `backend-skeleton/notarist-ingest/.../api/response/UploadUrlResponse.java` |

Same class name in two packages. Only one is used by `IngestionController` — must verify which is active.

---

## Finding OA-06 — Correlation ID Header Case Mismatch

**LATENT_RISK**

| Location | Header Value |
|---|---|
| `NotaristConstants.HEADER_CORRELATION_ID` | `"X-Correlation-ID"` (capital D) |
| `CorrelationIdFilter` | Uses `NotaristConstants.HEADER_CORRELATION_ID` → `"X-Correlation-ID"` |
| `application.yaml` | `correlation-id-header: X-Correlation-ID` (capital D) |
| `AssistantController` | `@RequestHeader(value = "X-Correlation-Id")` (lowercase d) |

`AssistantController` reads `X-Correlation-Id` (lowercase d). `CorrelationIdFilter` writes `X-Correlation-ID` (uppercase D). These are DIFFERENT HTTP headers.

**Impact:** The correlation ID from `CorrelationIdFilter` is never picked up by `AssistantController`'s request header. The controller generates a fresh `CorrelationId.generate()` for every request, breaking correlation ID propagation for the assistant flow.

**Note:** HTTP header names are case-insensitive per RFC 7230. However, Spring's `@RequestHeader` matching IS case-insensitive, so in practice this may not cause a runtime error. But the inconsistency is a maintenance risk and documentation error.

**Remediation:** Standardize all header references to use `NotaristConstants.HEADER_CORRELATION_ID`. Replace hardcoded header strings with the constant.

---

## Finding OA-07 — SSE Event Schema Consistency

### SSE Events Emitted by `AssistantController`

From `AssistantController.askStream()`, the SSE response emits:
- `ANSWER_TOKEN` — streaming text chunk
- `CITATION` — source reference
- `CONFIDENCE` — grounding confidence score
- `WARNING` — safety/quality warning
- `FOLLOW_UP` — suggested follow-up question
- `DONE` — stream complete
- `ERROR` — error event

### SSE Event Records in Code

| Record | Location | Fields |
|---|---|---|
| `SseCompleteEvent` | `backend-skeleton/notarist-assistant/.../response/` | `sessionId`, `queryId`, `warnings`, `completedAt` |
| `SseChunkEvent` | (likely in skeleton) | Token text chunk |
| `SseErrorEvent` | (likely in skeleton) | Error message, code |
| `SseEvent` (Design A) | `phase4-assistant/.../response/SseEvent.java` | Monolithic: `type`, `data`, `correlationId`, `sequenceNum` |

**Gap:** The controller's Javadoc lists 7 event types. Only 3 typed record variants are confirmed. The monolithic `SseEvent` (Design A) uses `Object data` — no type safety for the payload. Clients cannot validate event payload schema.

**Remediation:** Complete the typed SSE contract (Design B records) for all 7 event types and remove `SseEvent` (Design A). This is a Phase 6A.4 item (already tracked in architecture_drift_cleanup_report.md).

---

## Finding OA-08 — Enum Serialization Consistency

### Enums in API DTOs

| DTO | Field | Type | Serialized As |
|---|---|---|---|
| `AssistantRequest` | `maxClassificationLevel` | `String` | Free-form string |
| `AssistantRequest` | `documentTypeFilter` | `String` | Free-form string |
| `AssistantRequest` | `safetyMode` | `String` | Free-form string |
| `SearchResponse` | `intent` | `SearchIntent` | Enum (default: name) |
| `SearchResponse` | `groundingLevel` | `GroundingScore.Level` | Enum (default: name) |

`AssistantRequest` uses `String` for enum-valued fields and parses them in the controller using `Enum.valueOf(enumClass, value.toUpperCase())`. This approach:
- Is tolerant of invalid values (silently uses default)
- Not validated at the HTTP layer (`@NotNull`, `@Pattern` missing)
- Not documented in OpenAPI schema — clients won't know valid enum values

**Remediation:** Use typed enums in `AssistantRequest` with `@JsonDeserialize` or Jackson enum mapping, or add `@Pattern` validation with documented valid values.

---

## Finding OA-09 — `ApiResponse` Wrapper Consistency

Two `ApiResponse` classes exist:
1. `backend-skeleton/notarist-core/.../api/response/ApiResponse.java`
2. `backend-implementation/phase1-auth-document/notarist-core/.../api/response/ApiResponse.java`

Both exist in the shadow core module issue (tracked in architecture drift report). Controllers import from the skeleton version (canonical). Phase1 copy is unused by active code but could cause confusion.

---

## Nullable Consistency Check

| DTO | Nullable Fields | `@Nullable`/`@JsonInclude` Present |
|---|---|---|
| `AssistantRequest` | `sessionId`, `documentTypeFilter`, `safetyMode` may be null | No validation annotations |
| `SearchResponse` | `errorMessage`, `intent`, `normalizedQuery` may be null | No `@JsonInclude(NON_NULL)` |
| `AssistantResponse` | Unknown — not read in this scan | Unknown |

**Recommendation:** Add `@JsonInclude(JsonInclude.Include.NON_NULL)` to all API response records to prevent null fields from appearing in JSON output.

---

## Summary

| Finding | Classification | Priority |
|---|---|---|
| OA-01: No static OpenAPI spec file | CONFIG_RISK | P2 |
| OA-02: AssistantRequest vs AssistantQueryRequest mismatch | CRITICAL | P0 |
| OA-03: SearchResponse skeleton vs impl drift | LATENT_RISK | P2 |
| OA-04: IngestionJobStatusResponse duplication | LATENT_RISK | P2 |
| OA-05: UploadUrlResponse duplication | LATENT_RISK | P2 |
| OA-06: Correlation ID header case mismatch | LATENT_RISK | P1 |
| OA-07: SSE event schema incomplete (Design A still present) | CONFIG_RISK | P1 |
| OA-08: Enum fields as String in AssistantRequest | LATENT_RISK | P2 |
| OA-09: ApiResponse class duplication | LATENT_RISK | P2 |
