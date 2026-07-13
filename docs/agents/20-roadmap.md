# 20 — Roadmap & Change History

## Change history

| Version | Date | Description |
|---|---|---|
| v1 | 2026-05-23 | Initial project bootstrap; architecture pivot from KPI/datamart to legal-document intelligence (see [[01-system-overview]]) |
| — | 2026-05-24 | STEP 2–7.5 architecture fully decided and frozen; STEP 8A backend skeleton (127 files) generated; STEP 8B Phase 1 (auth + document) completed |
| — | 2026-07-03 | Deployable project structure assembled from generated code; a long sequence of pre-deployment defect fixes followed (bean conflicts, orphaned ports, FK constraints, `@Qualifier` disambiguation, config-prefix mismatches, schema/code alignment — see `git log`) |
| — | 2026-07-12 | Zero-defect build achieved across all 12 backend modules; React Native (Expo) frontend scaffold added; this doc set (`docs/agents/`) written |
| — | 2026-07-13 | Production QA audit (21 findings, `production_qa_audit_report.md`); 12 now fixed across 5 remediation rounds. Round 5 made the ingestion pipeline carry real data end to end and fixed the F11/F20 stall that had left it inert; repository's first automated tests added |
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

## Closed: the ingestion pipeline now carries real data end to end (2026-07-13)

The gap previously recorded here — OCR-confidence gating never reaching Qdrant, because every
stage fabricated its own stub input — is closed, together with audit findings F18, F11 and F20:

- Real per-chunk data now flows `ChunkWorker → EmbeddingWorker → IndexingWorker → Qdrant`:
  a `DocumentChunker` domain service, a `ChunkMetadata` persistence port + Postgres repository
  (Flyway `V6__chunk_index_ingest_columns.sql`), real NER/embedding runtime adapters, and Oracle
  `V003` adding the `IngestionJob.ocrConfidence`/`ocrObjectKey` columns the JPA entity was
  missing. `IndexingWorker` upserts real embeddings instead of a stub zero-vector, and
  `VectorIndexPort.ChunkPayload` carries `searchable`, so OCR gating actually reaches the index.

- **The keystone bug (F11/F20).** All of the above was inert: the pipeline stalled one step
  before Qdrant and nothing was ever indexed. `PipelineCoordinator` transitions a job to
  `completedStageFor(stage)` and then asks `nextPendingStage(newStatus)` what to enqueue — but
  there is no `EMBED_COMPLETED` status (the enum *and* the Oracle check constraint on
  `INGESTION_JOB.PIPELINE_STATUS` collapse it into `INDEX_PENDING`), so after embedding a job's
  status **is already** the next pending stage. `nextPendingStage` matched on `EMBED_PENDING`
  instead — a status that is never passed to it — so it returned empty and no INDEX queue row
  was ever created. The dead branch (F20) *was* the stall (F11). Fixed by routing
  `INDEX_PENDING → INDEX_PENDING`, the one status that maps to itself; the asymmetry is now
  documented on the method rather than left as a trap.

Verified by a full `./gradlew build --rerun-tasks` (12 modules) and by the repository's first
automated tests — see below.

## The test suite has started (`PipelineStateMachineTest`)

`backend/build.gradle.kts` now declares JUnit 5 for all subprojects (the `useJUnitPlatform()`
config already existed but no test dependency did), and `notarist-ingest` has 4 tests over
`PipelineStateMachine`. The headline test walks a job the way `PipelineCoordinator` drives it
and asserts it reaches `COMPLETED` — it was confirmed **red against the old mapping** (2
failures) and green after the fix, so it is a real regression guard, not a green rubber stamp.

This is the beachhead for next-step 3 below: the domain layer is pure and Spring-free, so it is
the cheapest place to keep adding tests.

## Known gaps / open questions

- **Test coverage is one class deep** (see [[18-testing-standard]]) — still the single largest
  gap between current state and the target standard. `PipelineStateMachine` is covered; every
  other domain policy (`DocumentStatusMachine`, `RetryPolicy`, `OcrConfidencePolicy`) and every
  worker, adapter and handler has no test.
- **The ingestion pipeline has never been run against live infrastructure.** Its data flow is
  now real and unit-tested at the state-machine level, but no Oracle/Postgres/Qdrant/MinIO was
  available in any session so far — no document has actually been ingested end to end. The
  audit's runtime findings stop at "context boots, fails only on external DB connect."
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

1. **Run the pipeline against live infrastructure** — `docker compose up` the stack and ingest
   one real document end to end. This is now the highest-value next action: the data flow is
   real but has never executed, and the remaining open audit findings (F4/F7 VPD policies,
   F9 audit-trail persistence, F12 Oracle pool) are all things only a live run will expose.
2. Work the remaining open audit findings in `production_qa_audit_report.md` — 8 left, with
   F4 (CRITICAL: VPD policy function `TENANT_ISOLATION_POLICY` referenced but never created),
   F7 and F9 the highest-severity.
3. Keep extending the domain test suite per [[18-testing-standard]] — `DocumentStatusMachine`,
   `RetryPolicy`, `OcrConfidencePolicy` next. The `PipelineStateMachine` tests already paid for
   themselves by pinning F11; note that finding sat undetected through a full audit *and* a
   remediation round precisely because nothing executed the pipeline.
4. Resolve the frontend TypeScript decision before more screens are added.
5. Wire ML sidecar health checks into `notarist-observability` if they aren't already there.
6. Decide and record whether the KPI/dashboard capability is revived, redefined around
   `DOKUMEN_LEGAL`, or formally dropped from `/CLAUDE.md`'s primary goal list.
7. Push `main` to `origin/main` when ready — it's currently several commits ahead, unpushed.
