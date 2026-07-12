# 18 — Testing Standard

## Current state

No automated test suite has been established in this repo yet (no `src/test` content
observed as of this doc set's writing — see [[20-roadmap]]). The standard below is the
target to build toward, not a description of what already exists; treat "add tests" as an
open, not-yet-started workstream rather than assuming coverage exists.

## Target standard, by layer

- **Domain layer** (`domain/model`, `domain/service`): pure unit tests, no Spring context, no
  mocking framework needed for most of it since these classes have zero framework
  dependency by design (see [[17-coding-standard]]). `PipelineStateMachine`,
  `DocumentStatusMachine`, `RetryPolicy`, `ChecksumValidator`, `OcrConfidencePolicy` are
  exactly the kind of class that should have direct, mock-free unit tests.
- **Application layer** (handlers, services, coordinators): unit tests with fakes/mocks for
  ports (`IngestJobRepository`, `VectorSearchPort`, `QueryEmbeddingPort`, etc.) — this is
  where the degrade-gracefully contracts (see [[06-backend-agent]]) get their actual
  verification: simulate a port throwing, assert the caller degrades rather than propagating.
- **Infrastructure layer** (JPA repos, adapters): integration tests against real backing
  services — Testcontainers for Oracle and PostgreSQL, a real (or containerized) Qdrant
  instance for `VectorSearchPort`/`VectorIndexPort` adapters. Do not mock the database for
  these — the prior architecture pivot and hybrid-DB decision (see [[02-architecture]]) exist
  precisely because layer boundaries matter here, and a mocked-DB test can't catch a VPD
  context or schema mismatch.
- **Contract tests**: request/response shape against the frozen OpenAPI spec (STEP 7.5) —
  a controller test that would pass against a shape different from the frozen contract is a
  bug in the test, not a green light.
- **Frontend**: component/screen tests for the 4 (eventually ~15) screens, with the SSE
  polyfill (see [[07-frontend-agent]]) tested against a simulated streaming response,
  including a dropped/malformed stream.

## What "tested" means for a change (used by [[08-qa-agent]])

1. New domain logic has a direct unit test, not just "the app didn't crash."
2. A new or changed port has at least one test exercising its documented failure/degradation
   path, if one is documented.
3. A change touching a frozen contract has a test asserting the contract shape, not just
   the happy-path value.
4. A security-relevant change (see [[10-security-agent]]) has a test proving the
   classification/redaction/masking actually applies — not just that the feature works when
   classification is ignored.

## What not to do

Don't add tests for scenarios that can't occur structurally (see the parent system's
guidance on validation at real boundaries only), and don't reach for mocking the database in
integration tests — that's the specific anti-pattern this standard exists to prevent, given
the hybrid-DB architecture's whole point is correctness at real layer boundaries.
