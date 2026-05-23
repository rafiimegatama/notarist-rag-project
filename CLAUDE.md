# PROJECT CONTEXT — NOTARIST RAG SYSTEM

## PROJECT NAME
NOTARIST RAG PLATFORM

## PROJECT TYPE
Enterprise Local RAG System untuk Kantor Notaris dan PPAT

## PRIMARY GOAL
Membangun sistem knowledge retrieval berbasis AI untuk:
- dokumen notaris
- akta
- staging datamart
- dashboard KPI
- frontend React Native
- backend Spring Boot
- Oracle 19C

## ARCHITECTURE

Frontend:
- React Native

Backend:
- Spring Boot 3
- Java 17

Database:
- Oracle 19C

Schema:
- BRANCHPERFSTAGINGDB
- BRANCHPERFAPPDB

RAG Stack:
- Qdrant
- bge-m3 embedding
- reranker
- local LLM

## GLOBAL RULES

1. Jangan asumsi nama kolom.
2. Jangan skip file.
3. Jangan hallucinate mapping.
4. Semua SQL Oracle 19C compatible.
5. Semua output simpan ke /generated.
6. Jangan modify source tanpa approval.
7. Semua query wajib explicit column.
8. Dilarang SELECT *.
9. Semua query wajib gunakan MAX(TIME_PR).
10. Gunakan ANALYSIS_FIRST mode.

## BUSINESS DOMAIN

Domain:
- Notaris
- PPAT
- Legal document
- Akta
- Sertifikat
- Fidusia
- Roya
- APHT
- SKMHT

## DATA CLASSIFICATION

- PUBLIC
- INTERNAL
- CONFIDENTIAL
- STRICTLY_CONFIDENTIAL

## OUTPUT STANDARD

Semua generated file wajib:
- markdown normalized
- RAG friendly
- chunk friendly
- source traceable

## GENERATED DIRECTORY

/generated/docs
/generated/sql
/generated/backend
/generated/json
/generated/openapi

## CHANGE HISTORY

| Version | Date | Description |
|---|---|---|
| v1 | 2026-05-23 | Initial project bootstrap |