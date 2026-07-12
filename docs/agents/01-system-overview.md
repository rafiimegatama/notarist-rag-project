# 01 — System Overview

## What this is

Notarist RAG Platform is an internal knowledge-retrieval system for an Indonesian
notaris/PPAT (notary + land-deed official) practice. It lets staff and officers search,
retrieve, and ask questions over legal documents with cited, grounded answers — not a
general chatbot, and (see pivot below) not a KPI/branch-performance datamart.

## The pivot (2026-05-23)

The project was initially scoped around `BRANCHPERFSTAGINGDB` / `BRANCHPERFAPPDB` —
branch/area/region KPI aggregation. That direction was explicitly abandoned. The unit of
analysis is now **`DOKUMEN_LEGAL`** (the legal document), not a transaction or a branch KPI
row. Concretely:

- Don't generate KPI banking aggregations, `TIME_PR` branch rollups, or Branch/Area/Region
  hierarchies unless they are demonstrably in service of a legal-document use case.
- The "dashboard KPI" line in `/CLAUDE.md`'s primary goal is legacy phrasing from before the
  pivot; treat legal-document intelligence as the primary goal and any KPI dashboard as a
  secondary, not-yet-committed capability.

## Core entities

- `DOKUMEN_LEGAL` (root entity), `AKTA`, `CLIENT`, `NOTARIS`, `PPAT`, `SERTIFIKAT`
- `OCR_RESULT`, `DOC_CHUNK`, `EMBEDDING_METADATA`
- `LEGAL_ENTITY_EXTRACT`, `SEMANTIC_TAG`, `AUDIT_LOG`
- Officer model: `PERSON_MASTER` → `NOTARIS_MASTER` + `PPAT_MASTER` (one person can hold
  multiple legal roles over time, tracked via `OFFICER_ROLE_HISTORY`)

## Document types (`AKTA` variants)

APHT, SKMHT, FIDUSIA, ROYA, AJB, APJB, APH, WASIAT, WAARMERKING, LEGALISASI, KUASA.

## Capabilities

- Document ingestion: upload → OCR → chunk → NER → embed → index (see [[02-architecture]])
- Semantic + keyword hybrid search with reranking and citation-first answers
- AI assistant with streaming (SSE), grounded strictly in retrieved citations
  (`HallucinationGuard` / `CitationValidator`)
- Regulation corpus (`REGULASI_MASTER` → BAB → PASAL → AYAT) as a second retrievable corpus
  alongside client documents
- Full audit trail on document access and sensitive-field reads

## Business domain vocabulary

See `docs/business/business_glossary.md` and `docs/business/semantic_dictionary.md` for the
canonical Indonesian legal/notarial terminology used across code, DDL, and these docs.
