# STEP 2 — DDL DESIGN
# NOTARIST RAG PLATFORM — HYBRID DATABASE ARCHITECTURE

**Version:** v1.0  
**Date:** 2026-05-23  
**Mode:** ANALYSIS_FIRST  
**Status:** DRAFT — Pending Approval  
**Scope:** Transactional Schema + RAG Schema + Vector Collection

---

## SUMMARY

Sistem menggunakan **Hybrid 3-Layer Database Architecture**:

| Layer | Technology | Tanggung Jawab |
|---|---|---|
| **Transactional** | Oracle 19C (schema: `NOTARIST`) | Legal master data, audit trail, versioning, access control |
| **RAG / Retrieval** | PostgreSQL (db: `notarist_rag`, schema: `rag`) | OCR, chunking, embedding metadata, AI interaction, search cache |
| **Vector** | Qdrant | Embedding vectors, similarity search, payload-based filtering |

Tiga layer ini **tidak redundan** — masing-masing memiliki peran yang eksklusif dan terhubung via `doc_id` (VARCHAR 36, UUID format) sebagai cross-database key.

---

## FINDINGS

### F-01 — Cross-Database Link
`DOC_MASTER.DOC_ID` di Oracle adalah **root key** yang direferensikan oleh semua
tabel di PostgreSQL (`doc_id VARCHAR(36)`) dan semua payload di Qdrant (`doc_id`).
Karena cross-database, FK constraint tidak dienforce di DB level — enforced di
application layer (Spring Boot service).

### F-02 — Oracle Tidak Menyimpan Teks OCR
Teks OCR, chunk, dan embedding metadata **tidak masuk Oracle**. Oracle hanya
menyimpan metadata struktural (`STATUS_DOKUMEN`, `KLASIFIKASI`, dll). Ini menjaga
Oracle tetap lean dan cepat untuk transactional queries.

### F-03 — PostgreSQL JSONB untuk Fleksibilitas
Field seperti `extracted_entities`, `auto_tags`, `bounding_box`, dan
`filter_applied` menggunakan JSONB. Ini memungkinkan schema fleksibel tanpa
ALTER TABLE ketika format NER atau filter berubah.

### F-04 — Qdrant Filter Harus Sinkron dengan PostgreSQL
Field `jenis_dokumen`, `jenis_akta`, `klasifikasi` di Qdrant payload **harus
selalu sinkron** dengan `rag.semantic_metadata`. Sinkronisasi ini dilakukan oleh
indexing pipeline, bukan trigger DB.

### F-05 — Audit Trail Split by Concern
- **Oracle** `AUDIT_TRAIL`: perubahan data master legal (INSERT/UPDATE/DELETE pada tabel master)
- **PostgreSQL** `rag.ai_interaction_audit`: aktivitas AI — siapa bertanya apa, dokumen apa yang terekspos
- **Oracle** `USER_DOC_ACCESS`: setiap akses dokumen fisik (VIEW/DOWNLOAD/PRINT)

### F-06 — `chunk_text_tsv` sebagai Hybrid Search Foundation
Kolom `doc_chunk.chunk_text_tsv` (GENERATED TSVECTOR) memungkinkan PostgreSQL
full-text search tanpa index terpisah. Dikombinasikan dengan Qdrant semantic
search via RRF fusion untuk hybrid retrieval.

---

## ENTITY TABLE

### Layer 1 — Oracle Transactional (15 tables)

| Table | Rows Est. | Tujuan |
|---|---|---|
| `USER_MASTER` | Ratusan | Pengguna internal sistem |
| `NOTARIS_MASTER` | Puluhan | Master pejabat notaris |
| `PPAT_MASTER` | Puluhan | Master PPAT |
| `CLIENT_MASTER` | Ribuan | Para pihak/klien |
| `SERTIFIKAT_MASTER` | Ribuan | Sertifikat properti |
| `DOC_MASTER` | Puluhan ribu | Root dokumen — semua file yang masuk |
| `AKTA_MASTER` | Puluhan ribu | Akta notarial (child DOC_MASTER) |
| `AKTA_PIHAK` | Ratusan ribu | Bridge: Akta ↔ Client per peran |
| `AKTA_SERTIFIKAT` | Puluhan ribu | Bridge: Akta ↔ Sertifikat |
| `DOC_RELATIONSHIP` | Ribuan | Relasi legal antar dokumen |
| `DOC_VERSION` | Ribuan | Riwayat versi dokumen |
| `TAG_MASTER` | Ratusan | Master tag semantik |
| `DOC_TAG_MAP` | Ratusan ribu | Bridge: Dokumen ↔ Tag |
| `USER_DOC_ACCESS` | Jutaan | Log akses user ke dokumen |
| `AUDIT_TRAIL` | Jutaan | Audit perubahan data master |

### Layer 2 — PostgreSQL RAG (10 tables + 2 views)

| Table / View | Rows Est. | Tujuan |
|---|---|---|
| `rag.ocr_result` | Jutaan | Teks OCR per halaman |
| `rag.legal_entity_extract` | Puluhan juta | NER entities per halaman |
| `rag.doc_chunk` | Jutaan | Potongan teks untuk indexing |
| `rag.embedding_metadata` | Jutaan | Metadata embedding Qdrant |
| `rag.semantic_metadata` | Puluhan ribu | Ringkasan semantik per dokumen |
| `rag.doc_processing_log` | Ratusan ribu | Log pipeline per tahap |
| `rag.search_session` | Jutaan | Sesi pencarian user |
| `rag.ai_query` | Jutaan | Setiap query yang dikirim |
| `rag.retrieval_result` | Puluhan juta | Hasil retrieval per query |
| `rag.ai_response` | Jutaan | Respons LLM |
| `rag.citation` | Jutaan | Kutipan chunk dalam respons |
| `rag.search_cache` | Ribuan | Cache query yang sering diulang |
| `rag.ai_interaction_audit` | Jutaan | Audit aktivitas AI |
| `rag.v_doc_indexing_status` | VIEW | Status pipeline per dokumen |
| `rag.v_retrieval_analytics` | VIEW | Analitik retrieval |

### Layer 3 — Qdrant Collection

| Collection | Tujuan |
|---|---|
| `notarist_legal_docs` | Primary collection — semua chunk dokumen legal |

---

## QDRANT COLLECTION DESIGN

### Collection: `notarist_legal_docs`

```yaml
collection_name: notarist_legal_docs

vectors_config:
  size: 1024
  distance: Cosine

optimizers_config:
  default_segment_number: 2
  indexing_threshold: 10000

hnsw_config:
  m: 16
  ef_construct: 100
  full_scan_threshold: 10000
```

### Indexed Payload Fields (untuk filtering efisien)

```json
{
  "jenis_dokumen":    "keyword",
  "jenis_akta":       "keyword",
  "klasifikasi":      "keyword",
  "doc_id":           "keyword",
  "notary_id":        "keyword",
  "tanggal_dokumen":  "datetime",
  "tags":             "keyword[]",
  "is_searchable":    "bool"
}
```

### Full Payload Schema (per vector point)

```json
{
  "chunk_id":           "uuid",
  "doc_id":             "uuid-string",
  "jenis_dokumen":      "AKTA | SOP | REGULASI | PERJANJIAN | SERTIFIKAT_SCAN | LAIN",
  "jenis_akta":         "APHT | SKMHT | FIDUSIA | ROYA | AJB | null",
  "klasifikasi":        "PUBLIC | INTERNAL | CONFIDENTIAL | STRICTLY_CONFIDENTIAL",
  "notary_id":          "uuid-string | null",
  "notary_name":        "string | null",
  "client_names":       ["string"],
  "nomor_akta":         "string | null",
  "certificate_number": "string | null",
  "tanggal_dokumen":    "ISO 8601 date | null",
  "page_number_start":  "integer",
  "page_number_end":    "integer",
  "chunk_index":        "integer",
  "chunk_type":         "PARAGRAPH | CLAUSE | ARTICLE | HEADER | TABLE | LAIN",
  "chunk_text":         "string",
  "token_count":        "integer",
  "source_filename":    "string",
  "ocr_confidence":     "float | null",
  "tags":               ["string"],
  "model_name":         "bge-m3",
  "model_version":      "string",
  "indexed_at":         "ISO 8601 datetime",
  "is_searchable":      "boolean"
}
```

### Contoh Filter Query

**Cari hanya dalam dokumen APHT yang CONFIDENTIAL:**
```json
{
  "filter": {
    "must": [
      {"key": "jenis_akta",   "match": {"value": "APHT"}},
      {"key": "klasifikasi",  "match": {"any": ["CONFIDENTIAL", "INTERNAL"]}},
      {"key": "is_searchable","match": {"value": true}}
    ]
  }
}
```

**Cari dalam dokumen tanggal tertentu:**
```json
{
  "filter": {
    "must": [
      {"key": "jenis_dokumen", "match": {"value": "AKTA"}},
      {"key": "tanggal_dokumen", "range": {
        "gte": "2024-01-01T00:00:00Z",
        "lte": "2024-12-31T23:59:59Z"
      }}
    ]
  }
}
```

---

## RELATIONSHIP

### Cross-Layer Relationship Map

```
┌─────────────────────────────────────────────────────────────────┐
│ ORACLE 19C — NOTARIST schema                                    │
│                                                                 │
│  USER_MASTER ──< USER_DOC_ACCESS >──  DOC_MASTER               │
│                                           │                     │
│  NOTARIS_MASTER ──────────────────────< AKTA_MASTER            │
│  PPAT_MASTER ─────────────────────────<                        │
│                                           │                     │
│  CLIENT_MASTER ──< AKTA_PIHAK >──────────┤                     │
│  SERTIFIKAT_MASTER <── AKTA_SERTIFIKAT ──┤                     │
│                                           │                     │
│  DOC_MASTER <── DOC_RELATIONSHIP ──> DOC_MASTER                │
│  DOC_MASTER ──< DOC_VERSION                                     │
│  DOC_MASTER <── DOC_TAG_MAP ──> TAG_MASTER                     │
│              ↑                                                  │
│         DOC_ID (root key, VARCHAR2 36)                         │
└─────────────────────────────────────────────────────────────────┘
                           │
                    doc_id (VARCHAR 36)
                    [application-enforced FK]
                           │
┌─────────────────────────────────────────────────────────────────┐
│ POSTGRESQL — notarist_rag.rag schema                            │
│                                                                 │
│  ocr_result (doc_id) ──< legal_entity_extract                  │
│  doc_chunk (doc_id) ──< embedding_metadata                     │
│                                                                 │
│  semantic_metadata (doc_id) — ringkasan per dokumen            │
│  doc_processing_log (doc_id) — pipeline per dokumen            │
│                                                                 │
│  search_session ──< ai_query ──< retrieval_result              │
│                         └──< ai_response ──< citation          │
│                                                                 │
│  ai_query/response ──< ai_interaction_audit                    │
│                                                                 │
│       qdrant_point_id                                           │
│            ↓                                                    │
└─────────────────────────────────────────────────────────────────┘
                           │
               qdrant_point_id (UUID, Qdrant payload)
                           │
┌─────────────────────────────────────────────────────────────────┐
│ QDRANT — notarist_legal_docs collection                         │
│                                                                 │
│  [Point: qdrant_point_id]                                       │
│    vector: float[1024]   (bge-m3 embedding)                    │
│    payload: {chunk_id, doc_id, jenis_dokumen, ...}             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Document Legal Chain Relationships

```
Oracle DOC_RELATIONSHIP — contoh relasi nyata:

SKMHT ─────PRECEDES──────▶ APHT ─────CANCELS──────▶ ROYA
  │                          │
  │                          └──references──▶ SERTIFIKAT
  │
APJB ──PRECEDES──▶ AJB ──references──▶ SERTIFIKAT

PERJANJIAN_KREDIT ──RELATED_TO──▶ FIDUSIA

DOC_V1 ──SUPERSEDES──▶ DOC_V2 (dokumen yang diperbarui)
DOC_LAMPIRAN ──ATTACHMENT_OF──▶ DOC_UTAMA
```

---

## METADATA LIFECYCLE

```
[TAHAP 1] UPLOAD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Oracle:     DOC_MASTER INSERT            (STATUS: PENDING_OCR)
  Oracle:     DOC_VERSION INSERT           (VERSION: 1, TYPE: INITIAL)
  Oracle:     USER_DOC_ACCESS INSERT       (TYPE: UPLOAD)
  PostgreSQL: doc_processing_log INSERT    (STAGE: UPLOAD, STATUS: COMPLETED)

[TAHAP 2] OCR EXTRACTION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  PostgreSQL: ocr_result INSERT            (satu baris per halaman)
  PostgreSQL: doc_processing_log UPDATE    (STAGE: OCR, STATUS: COMPLETED)
  Oracle:     DOC_MASTER UPDATE            (STATUS: PENDING_EXTRACTION)

[TAHAP 3] NER / ENTITY EXTRACTION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  PostgreSQL: legal_entity_extract INSERT  (banyak baris per dokumen)
  Oracle:     AKTA_MASTER INSERT/UPDATE    (jika entitas teridentifikasi)
  Oracle:     CLIENT_MASTER INSERT/UPDATE  (jika nama pihak ditemukan)
  Oracle:     AKTA_PIHAK INSERT            (mapping pihak ke akta)
  PostgreSQL: doc_processing_log UPDATE    (STAGE: NER, STATUS: COMPLETED)
  Oracle:     DOC_MASTER UPDATE            (STATUS: PENDING_EMBEDDING)

[TAHAP 4] CLASSIFICATION & TAGGING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Oracle:     DOC_TAG_MAP INSERT           (auto tags dari AI)
  Oracle:     DOC_MASTER UPDATE            (KLASIFIKASI updated)
  PostgreSQL: semantic_metadata INSERT     (jenis_dokumen, auto_tags awal)

[TAHAP 5] CHUNKING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  PostgreSQL: doc_chunk INSERT             (N baris per dokumen)
  PostgreSQL: doc_processing_log UPDATE    (STAGE: CHUNKING, STATUS: COMPLETED)

[TAHAP 6] EMBEDDING & INDEXING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  PostgreSQL: embedding_metadata INSERT    (STATUS: PENDING)
  Qdrant:     UPSERT point                 (vector + payload)
  PostgreSQL: embedding_metadata UPDATE    (STATUS: INDEXED, indexed_at set)
  PostgreSQL: doc_chunk UPDATE             (is_indexed: TRUE)
  PostgreSQL: doc_processing_log UPDATE    (STAGE: INDEXING, STATUS: COMPLETED)
  Oracle:     DOC_MASTER UPDATE            (STATUS: ACTIVE)
  PostgreSQL: semantic_metadata UPDATE     (is_searchable: TRUE, totals updated)

[DOKUMEN AKTIF — SIAP DICARI]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Oracle:     DOC_MASTER.STATUS = 'ACTIVE'
  PostgreSQL: semantic_metadata.is_searchable = TRUE
  Qdrant:     payload.is_searchable = true
  → Dokumen terindeks di 3 layer

[TAHAP 7] RETRIEVAL (saat user search)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  PostgreSQL: search_session INSERT/UPDATE
  PostgreSQL: ai_query INSERT
  Qdrant:     ANN search → TOP-K chunks
  PostgreSQL: full-text search → BM25 results
  PostgreSQL: retrieval_result INSERT      (merge + rerank scores)
  LLM:        generate response
  PostgreSQL: ai_response INSERT
  PostgreSQL: citation INSERT              (per chunk yang dikutip)
  Oracle:     USER_DOC_ACCESS INSERT       (per dokumen yang diakses)
  PostgreSQL: ai_interaction_audit INSERT
```

---

## RECOMMENDATION

### REC-01 — Schema Name untuk Oracle
Konfirmasi schema name di Oracle. Opsi:
- `NOTARIST` — bersih, sesuai domain
- `NOTARIST_APP` — lebih eksplisit jika ada multi-schema lain
- Gunakan schema existing (`BRANCHPERFAPPDB`) hanya jika ada alasan integrasi

### REC-02 — Indeks Qdrant yang Wajib Diaktifkan
Sebelum indexing bulk, wajib aktifkan payload index di Qdrant untuk:
`jenis_dokumen`, `jenis_akta`, `klasifikasi`, `doc_id`, `is_searchable`
Tanpa ini, filter pada collection besar akan lambat (full scan).

### REC-03 — Partisi Oracle untuk ACCESS LOG
`USER_DOC_ACCESS` dan `AUDIT_TRAIL` akan tumbuh sangat cepat. Pertimbangkan:
- Partisi by `ACCESS_TIME` (range partition by month/year)
- Retention policy: purge data > 5 tahun sesuai kebijakan kearsipan notaris

### REC-04 — PostgreSQL Maintenance
- `search_cache`: buat scheduled job untuk DELETE WHERE expires_at < NOW()
- `doc_processing_log`: purge log lama (> 6 bulan) untuk log status COMPLETED
- `retrieval_result`: archive/purge data > 1 tahun untuk hemat storage

### REC-05 — Chunking Strategy (konfirmasi sebelum implementasi)
Strategi chunking `simple` tsvector digunakan di PostgreSQL. Untuk domain hukum
Indonesia, evaluasi apakah perlu custom dictionary atau tetap `simple`.
Custom dictionary akan meningkatkan relevansi keyword search untuk istilah
seperti "hak tanggungan", "roya", "fidusia".

---

## FILE OUTPUT

| File | Path | Keterangan |
|---|---|---|
| Oracle DDL | `/generated/sql/oracle_transactional_schema.sql` | 15 tabel, Oracle 19C |
| PostgreSQL DDL | `/generated/sql/postgres_rag_schema.sql` | 10 tabel, 2 views |
| DDL Design Doc | `/generated/docs/step2_ddl_design.md` | File ini |
| Architecture Analysis | `/generated/docs/rag_architecture_analysis.md` | STEP 1 output |

---

## STATUS

```
STEP 1 — ANALYZE NEW DOMAIN    ✅ COMPLETE
STEP 2 — DDL DESIGN            ✅ COMPLETE (pending approval)
STEP 3 — API DESIGN            ⏸ WAITING APPROVAL
STEP 4 — FRONTEND SCREENS      ⏸ WAITING APPROVAL
STEP 5 — RAG PIPELINE CODE     ⏸ WAITING APPROVAL
```

**5 pertanyaan untuk konfirmasi sebelum STEP 3:**

1. Oracle schema name: `NOTARIST` atau lainnya?
2. Apakah `NOTARIS_MASTER` dan `PPAT_MASTER` perlu digabung (satu tabel `OFFICER_MASTER` dengan type)?
3. Apakah perlu tabel `REGULASI_MASTER` terpisah (untuk SOP dan regulasi eksternal yang punya atribut khusus)?
4. Apakah field `NILAI_TRANSAKSI` di `AKTA_MASTER` bersifat CONFIDENTIAL dan perlu enkripsi?
5. Apakah perlu role-based access control di level tabel, atau cukup di Spring Boot service layer?

---

*Generated by: NOTARIST RAG PLATFORM — ANALYSIS_FIRST mode*  
*File: /generated/docs/step2_ddl_design.md*  
*Date: 2026-05-23*
