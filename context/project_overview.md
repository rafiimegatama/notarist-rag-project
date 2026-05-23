# PROJECT OVERVIEW

## PURPOSE

Platform RAG lokal untuk kantor notaris dan PPAT.

Sistem digunakan untuk:
- knowledge retrieval
- pencarian dokumen
- semantic search
- dashboard KPI
- ETL staging ke app DB
- backend API
- frontend mobile

## MAIN COMPONENTS

1. React Native frontend
2. Spring Boot backend
3. Oracle staging schema
4. Oracle app schema
5. ETL stored procedure
6. Vector database
7. Local LLM

## BUSINESS ENTITIES

- Akta
- Sertifikat
- PPAT
- Notaris
- Client
- Wilayah
- Branch
- Area
- Region

## DATA FLOW

Frontend
→ Backend API
→ Oracle APP DB
→ Oracle STAGING
→ ETL
→ RAG Metadata
→ Vector DB

## TARGET OUTPUT

- mapping UI → staging
- app DB DDL
- ETL stored procedure
- Spring Boot API
- OpenAPI spec
- RAG metadata