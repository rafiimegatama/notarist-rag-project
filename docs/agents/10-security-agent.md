# 10 — Security Agent

## Scope

Authentication, authorization, encryption, data classification enforcement, and audit —
across all three data layers (Oracle, PostgreSQL, Qdrant).

## Data classification

Every field/document: `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `STRICTLY_CONFIDENTIAL`
(see [[00-project-rules]]). This drives three independent enforcement points — a review
must check all three, not just one:

1. **Oracle VPD** — row/column-level policy via `DBMS_SESSION.SET_CONTEXT`, applied per
   module through a duplicated `VpdContextApplier` (see [[06-backend-agent]]).
2. **Qdrant payload filter** — classification is part of the vector payload; a query must
   filter to the requesting user's max classification level before results return
   (`maxClassificationLevel` parameter, see `VectorSearchPort` in [[02-architecture]]).
3. **Application-level field masking (S2)** — role-based masking for `ALAMAT`, `NO_TELP`,
   `EMAIL`.

## Encryption tiers

- **S1** (Oracle TDE + application-level encryption): `NIK`, `NPWP`, `NILAI_TRANSAKSI`,
  `NOMOR_REKENING` — encrypted at rest and in the application layer, not just TDE.
- **S2** (application masking per role): `ALAMAT`, `NO_TELP`, `EMAIL` — masked based on
  requesting role, not encrypted at rest.
- **Chunk redaction**: any text destined for PostgreSQL chunk storage or Qdrant **must** be
  redacted of S1/S2 fields before it leaves the ingestion pipeline. This is checked at the
  `ChunkWorker`/`EmbeddingWorker` boundary in `notarist-ingest` — a redaction gap here is a
  security defect, not a quality nit, and blocks a release.

## RBAC

Hybrid, three enforcement layers acting together (not alternatives): Spring Boot JWT (RS256,
refresh rotation, deny-list on logout) at the API boundary, Oracle VPD at the transactional
data boundary, Qdrant payload filter at the vector boundary. Roles: `STAFF`, `NOTARIS`,
`PPAT_OFFICER`, `PIMPINAN`, `ADMIN`.

## Audit

Every document access and every sensitive-field read is logged to `NOTARIST_SEC` schema
(`AUDIT_TRAIL`, `USER_DOC_ACCESS`, `SENSITIVE_FIELD_ACCESS_LOG`) via `AuditEventPayload` +
`ApplicationEventPublisher` (see [[06-backend-agent]]) — never as an afterthought bolted onto
a controller; the publish call lives in the same handler that performs the access.

## Review checklist for any change touching auth, PII, or classification

1. Does a new field carry an explicit classification, or does it silently default to
   something too permissive?
2. Does chunk/embedding text get redacted before persistence for any newly-ingested field?
3. Does the query path apply the classification filter, or could a broader-than-intended
   result set leak through an unfiltered code path (e.g. a new search variant that bypasses
   `SemanticRetriever`)?
4. Is the audit event actually published, not just modeled?
5. JWT: does token refresh actually rotate and invalidate the prior refresh token (not just
   issue a new access token)?

## Handoff

Security review is a gate before [[08-qa-agent]] signs off on anything touching auth,
encryption, or classification — it is not folded silently into general QA.
