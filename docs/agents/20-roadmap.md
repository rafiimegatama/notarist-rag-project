# 20 — Roadmap & Change History

## Change history

| Version | Date | Description |
|---|---|---|
| v1 | 2026-05-23 | Initial project bootstrap; architecture pivot from KPI/datamart to legal-document intelligence (see [[01-system-overview]]) |
| — | 2026-05-24 | STEP 2–7.5 architecture fully decided and frozen; STEP 8A backend skeleton (127 files) generated; STEP 8B Phase 1 (auth + document) completed |
| — | 2026-07-03 | Deployable project structure assembled from generated code; a long sequence of pre-deployment defect fixes followed (bean conflicts, orphaned ports, FK constraints, `@Qualifier` disambiguation, config-prefix mismatches, schema/code alignment — see `git log`) |
| — | 2026-07-12 | Zero-defect build achieved across all 12 backend modules; React Native (Expo) frontend scaffold added; this doc set (`docs/agents/`) written |
| — | 2026-07-12 | Commit `5d686f8`: cross-tenant/security/build defect fixes (JWT filter chain wiring, `SearchController` cross-tenant header trust, `UserJpaEntity` JPA mapping), OCR policy relocated to `notarist-core`, real query-time embedding wiring, real `QdrantIndexAdapter`, plus two compile-breaking call sites (see below) fixed before commit |

## Reconciling memory vs. actual state (important)

Persistent memory (`step8b_progress.md`, dated 2026-05-24) states Phase 1 is done and
Phase 2 (`notarist-ingest`) is "next." **That is stale.** As of this doc set's writing,
`notarist-ingest` is fully implemented: `PipelineCoordinator`, all 5 stage workers
(`OcrWorker`, `ChunkWorker`, `NerWorker`, `EmbeddingWorker`, `IndexingWorker`), dead-letter
handling, retry policy, duplicate detection, and Oracle/Postgres persistence adapters are all
present and wired. `notarist-search`, `notarist-assistant`, `notarist-regulation`, and
`notarist-audit` are likewise built out, not just scaffolded. Treat the module file listing
under `backend/` as ground truth over the phase-tracking memory; the memory has been updated
to point here rather than repeating the stale phase claim.

## What just landed (commit `5d686f8`, 2026-07-12)

A prior audit pass (3 parallel audit agents, per the user) had fixed a batch of real
defects across the backend and left it uncommitted. Before committing, every claimed fix was
independently re-verified against the actual code rather than trusted at face value — the
audit's own summary ("everything compiles cleanly, 18/19 tasks done") turned out to be
**inaccurate**: a real Gradle compile (worked around a sandbox `build/`-directory permission
issue via an init script redirecting `buildDir`) found `notarist-ingest` did not compile.

Verified correct, as claimed:
- `JwtAuthenticationFilter` wired into the Spring Security chain (`SecurityConfig`), with its
  Spring Boot auto-registration explicitly disabled to avoid double execution.
- `SearchController` cross-tenant header trust fixed — tenant/user identity now come from
  `VpdContextHolder`'s authenticated principal, not a client-supplied header.
- `UserJpaEntity`'s invalid JPA `mappedBy` fixed by switching to an owning-side `@JoinColumn`.
- `PipelineStateMachine`'s `EMBED_PENDING` completion routing.
- OCR policy relocation (`OcrConfidencePolicy`/`OcrReviewStatus` → `notarist-core/domain/policy`)
  and real query-time embedding wiring (`QueryEmbeddingPort` + `QueryEmbeddingRuntimeAdapter`,
  `SemanticRetriever` degrading gracefully on failure) — both self-consistent, no dangling
  references to the old package.
- `QdrantIndexAdapter` replaced the no-op stub with a real Qdrant HTTP client.

Found broken, not caught by the audit's own verification, and fixed before commit:
- `IngestionJobRepositoryImpl.toDomain()` called `IngestionJob.reconstruct(...)` with the old
  arity — `IngestionJob` had gained two fields (`ocrConfidence`, `ocrObjectKey`) that this call
  site was never updated for. Fixed by passing `null` for both (see gap below — there's no JPA
  column for either yet, so this is honest, not a real value).
- `ChunkWorker.produceChunkMetadata()` called the `ChunkMetadata` constructor with 14 args
  against a 24-component record (10 fields had been added: `tenantId`, `documentType`,
  `classificationLevel`, `chunkText`, `ocrConfidence`, `reviewStatus`, `searchable`,
  `embedding`, `embeddingModel`, `embeddedAt`). Fixed with stub-appropriate placeholders
  (`reviewStatus = LOW_CONFIDENCE_REVIEW`, `searchable = false`, embedding fields `null`).

Both are now committed with all 12 backend modules verified to compile via a real `javac`
pass, not just static read-through.

## Still-open gap: OCR-confidence gating never actually reaches Qdrant

The audit's checklist listed exactly one open item — "wire OCR confidence gating in
`QdrantIndexAdapter`" — but the real gap is larger than a missing delegate call:

- `QdrantIndexAdapter.toQdrantPoint()` hardcodes `is_searchable = true` despite its own class
  Javadoc claiming it's "set from `OcrReviewStatus` on the chunk payload." `OcrConfidencePolicy`
  is imported and never used.
- It can't be wired yet anyway: `VectorIndexPort.ChunkPayload` (the record `toQdrantPoint`
  actually receives) carries no confidence/review-status field at all.
- `ChunkMetadata` *does* carry `ocrConfidence`/`reviewStatus`/`searchable` now, but nothing
  reads it — `ChunkWorker` builds it and discards it (no repository persists `ChunkMetadata`),
  and `IndexingWorker` still fabricates a single stub zero-vector chunk per job
  (`buildIndexableChunks()`) from `IngestionJob` fields directly, ignoring per-chunk data
  entirely. `EmbeddingWorker` is the same shape — stub input, never reads real chunk text.

Closing this properly means: a new Oracle migration + JPA columns for `IngestionJob.ocrConfidence`/
`ocrObjectKey`, a `ChunkMetadata` persistence port/repository, and rewiring
`ChunkWorker → EmbeddingWorker → IndexingWorker` to pass real per-chunk data through the
pipeline instead of each stage independently fabricating stub input. This was scoped as a
separate, larger follow-up rather than folded into the compile fix above.

## Known gaps / open questions

- **No automated test suite exists yet** (see [[18-testing-standard]]) — this is the single
  largest gap between current state and the target standard.
- **Frontend TypeScript vs. JavaScript is unresolved** — STEP 5 specified TypeScript; the
  actual Expo scaffold added 2026-07-12 is plain `.js` (see [[07-frontend-agent]]). Needs an
  explicit decision, not a silent default either way.
- **Frontend is a 4-screen slice** (Home, Login, Documents, Assistant) against a STEP 5 plan
  of 15 screens + 5 modals with Bottom Tab + Modal Stack navigation, React Query, and
  Zustand — none of the latter three confirmed wired yet.
- **KPI/dashboard capability** — deprioritized by the pivot (see [[01-system-overview]]), not
  deleted from the original primary-goal framing in `/CLAUDE.md`. Needs an explicit decision
  if it's ever revived: is it a `DOKUMEN_LEGAL`-scoped view, or does it reintroduce the
  branch/KPI datamart that was explicitly rejected?
- **ML sidecars (PaddleOCR, IndoBERT NER, reranker) are not in `infra/docker/docker-compose.yml`**
  yet, only `minio`/`postgres`/`qdrant`/`ollama` are — confirm whether they're deployed
  separately or still owed as a devops task (see [[11-devops-agent]]).

## Suggested next steps, in priority order

1. Close the OCR-confidence-gating gap above: migration + JPA columns for `IngestionJob`,
   a `ChunkMetadata` persistence port, and real data flow through
   `ChunkWorker → EmbeddingWorker → IndexingWorker → QdrantIndexAdapter`. This is the actual
   remaining correctness gap in the ingestion pipeline, not a one-line delegate call.
2. Resolve the frontend TypeScript decision before more screens are added.
3. Stand up the test suite per [[18-testing-standard]], starting with the domain layer
   (cheapest, highest-value: `PipelineStateMachine`, `DocumentStatusMachine`, `RetryPolicy`,
   `OcrConfidencePolicy`) — a unit test on `ChunkWorker`'s constructor call would have caught
   the compile break above immediately, before it ever reached a commit review.
4. Wire ML sidecar health checks into `notarist-observability` if they aren't already there.
5. Decide and record whether the KPI/dashboard capability is revived, redefined around
   `DOKUMEN_LEGAL`, or formally dropped from `/CLAUDE.md`'s primary goal list.
6. Push `main` to `origin/main` when ready — it's currently several commits ahead, unpushed.
