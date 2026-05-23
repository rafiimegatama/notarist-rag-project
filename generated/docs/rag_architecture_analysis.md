# RAG ARCHITECTURE ANALYSIS
# NOTARIST RAG PLATFORM — INTERNAL LEGAL DOCUMENT INTELLIGENCE

**Version:** v1.0  
**Date:** 2026-05-23  
**Mode:** ANALYSIS_FIRST  
**Status:** DRAFT — Pending Approval  
**Scope:** STEP 1 — Domain Analysis & Architecture Blueprint

---

## SUMMARY

Sistem ini adalah **Internal Notaris RAG Platform** — sebuah knowledge intelligence platform
untuk kantor notaris dan PPAT yang berfokus pada:

- Pengelolaan dokumen legal secara semantik
- Ekstraksi informasi otomatis via OCR dan NLP
- Pencarian berbasis AI (RAG: Retrieval-Augmented Generation)
- AI assistant untuk staf internal notaris

Sistem **bukan** branch performance datamart. Tidak ada KPI banking, tidak ada agregasi
TIME_PR, tidak ada hierarki Branch/Area/Region sebagai dimensi utama.

Unit analisis utama adalah **DOKUMEN LEGAL**, bukan transaksi atau kinerja cabang.

---

## FINDINGS

### F-01 — Primary Data Subject
Entitas utama adalah `DOKUMEN_LEGAL`. Semua fitur sistem (OCR, search, RAG, AI assistant)
berpusat pada lifecycle dokumen legal notaris.

### F-02 — Document Heterogeneity
Sistem harus menangani tipe dokumen yang sangat beragam:
- Akta notarial (APHT, SKMHT, Fidusia, Roya, Jual Beli, dll.)
- Sertifikat kepemilikan
- SOP internal kantor
- Regulasi eksternal (Permenkumham, UUHT, PP BPN)
- Surat kuasa, perjanjian pendahuluan

Setiap tipe dokumen memiliki struktur field yang berbeda — sistem harus adaptif
terhadap schema dokumen yang tidak seragam.

### F-03 — OCR is First-Class Citizen
Mayoritas dokumen masuk dalam format scan/PDF non-selectable. OCR bukan fitur
tambahan — ini adalah **ingestion gateway utama**. Kualitas OCR menentukan kualitas
retrieval.

### F-04 — Dual-Layer Search
Sistem perlu mendukung dua mode pencarian:
1. **Keyword/structured search** — berdasarkan NOMOR_AKTA, CLIENT_NAME, tanggal
2. **Semantic search** — berdasarkan makna hukum, konteks, sinonim legal

Kedua mode harus bisa digabung (hybrid search).

### F-05 — Legal Terminology Sensitivity
Domain notaris memiliki terminologi khusus (APHT, SKMHT, Roya, Fidusia, Waarmerking)
yang tidak umum dalam model bahasa generik. Sistem embedding (bge-m3) dan LLM
perlu dikondisikan dengan domain knowledge legal Indonesia.

### F-06 — Relationship Between Documents is Critical
Dokumen tidak berdiri sendiri. Roya berkaitan dengan APHT sebelumnya. SKMHT
adalah pendahulu APHT. Fidusia berkaitan dengan perjanjian kredit. Sistem harus
memodelkan relasi antar-dokumen secara eksplisit.

### F-07 — Audit & Confidentiality Requirements
Dokumen notaris bersifat rahasia. Sistem wajib:
- Mencatat setiap akses (audit trail)
- Memberi label klasifikasi (INTERNAL, CONFIDENTIAL, STRICTLY_CONFIDENTIAL)
- Mendukung access control berbasis peran (staf, notaris, pimpinan)

### F-08 — Chunk Strategy Sangat Berpengaruh
Dokumen legal panjang dan terstruktur (pasal, ayat, klausul). Chunking naif
(fixed-size) akan memotong konteks hukum. Perlu chunking berbasis struktur legal:
per pasal, per klausul, per halaman dengan overlap semantik.

---

## ENTITY TABLE

### A. Core Entities

| Entity | ID Field | Deskripsi | Sumber Data |
|---|---|---|---|
| `DOKUMEN_LEGAL` | `DOC_ID` | Parent entity semua dokumen yang masuk sistem | Upload / ETL |
| `AKTA` | `NOMOR_AKTA` | Dokumen akta notarial resmi | Upload / OCR |
| `CLIENT` | `CLIENT_ID` | Pihak dalam akta (pembeli, penjual, debitur, kreditur) | OCR / Manual entry |
| `NOTARIS` | `NOTARY_ID` | Pejabat pembuat akta. Identitas dari dokumen | Master data |
| `PPAT` | `PPAT_ID` | Pejabat Pembuat Akta Tanah. Bisa berbeda dari Notaris | Master data |
| `SERTIFIKAT` | `CERTIFICATE_NUMBER` | Bukti kepemilikan tanah/properti direferensikan akta | OCR / Manual |
| `PERJANJIAN` | `PERJANJIAN_ID` | Dokumen perjanjian yang bukan akta formal | Upload |
| `SOP` | `SOP_ID` | Standar Operasional Prosedur internal kantor | Upload |
| `REGULASI` | `REGULASI_ID` | Peraturan hukum eksternal (UU, PP, Permen) | Upload |

### B. Legal Document Type Registry

| Kode | Nama Lengkap | Kategori | Relasi Kunci |
|---|---|---|---|
| `APHT` | Akta Pemberian Hak Tanggungan | Hak Tanggungan | Didahului SKMHT (opsional) |
| `SKMHT` | Surat Kuasa Membebankan Hak Tanggungan | Hak Tanggungan | Mendahului APHT |
| `FIDUSIA` | Akta Fidusia | Jaminan Fidusia | Berkaitan perjanjian kredit |
| `ROYA` | Akta Roya / Pencoretan | Rilis Jaminan | Membatalkan APHT sebelumnya |
| `AJB` | Akta Jual Beli | Peralihan Hak | Mereferensikan Sertifikat |
| `APJB` | Akta Perjanjian Jual Beli | Peralihan Hak | Mendahului AJB |
| `APH` | Akta Pemberian Hibah | Hibah | Mereferensikan Sertifikat |
| `WASIAT` | Akta Wasiat | Waris | Bisa mereferensikan multi-aset |
| `WAARMERKING` | Waarmerking | Pendaftaran | Melampirkan dokumen eksternal |
| `LEGALISASI` | Legalisasi Tanda Tangan | Autentikasi | Tanda tangan pihak |
| `KUASA` | Surat Kuasa | Kuasa | Memberikan wewenang ke pihak lain |

### C. RAG & AI Entities

| Entity | ID Field | Deskripsi |
|---|---|---|
| `OCR_RESULT` | `OCR_ID` | Output raw teks dari proses OCR per dokumen |
| `DOC_CHUNK` | `CHUNK_ID` | Potongan teks dokumen yang diindeks ke vector DB |
| `EMBEDDING_METADATA` | `EMBEDDING_ID` | Metadata rekaman embedding di Qdrant |
| `SEMANTIC_TAG` | `TAG_ID` | Label semantik yang dilekatkan ke dokumen atau chunk |
| `LEGAL_ENTITY_EXTRACT` | `ENTITY_EXTRACT_ID` | Hasil NER: nama, tanggal, nomor, lokasi yang diekstrak |
| `SEARCH_SESSION` | `SESSION_ID` | Sesi pencarian satu user (untuk audit dan analytics) |
| `AI_QUERY` | `QUERY_ID` | Query yang dikirim ke RAG pipeline |
| `AI_RESPONSE` | `RESPONSE_ID` | Jawaban yang dihasilkan LLM beserta citation |
| `CITATION` | `CITATION_ID` | Referensi chunk spesifik yang digunakan dalam respons |
| `AUDIT_LOG` | `AUDIT_ID` | Rekaman setiap akses atau aksi pada dokumen |

### D. Classification & Taxonomy Entities

| Entity | Deskripsi |
|---|---|
| `JENIS_DOKUMEN` | Tipe dokumen (Akta, SOP, Regulasi, dll.) |
| `KLASIFIKASI_KERAHASIAAN` | PUBLIC / INTERNAL / CONFIDENTIAL / STRICTLY_CONFIDENTIAL |
| `KATEGORI_HUKUM` | Pengelompokan hukum: Hak Tanggungan, Fidusia, Peralihan Hak, dll. |
| `STATUS_DOKUMEN` | DRAFT / ACTIVE / SUPERSEDED / ARCHIVED / DELETED |

---

## RELATIONSHIP

### R-01 — Document Hierarchy

```
DOKUMEN_LEGAL  (parent — setiap file yang masuk)
    │
    ├──▶ AKTA          (dokumen notarial formal)
    ├──▶ PERJANJIAN    (dokumen non-akta)
    ├──▶ SOP           (dokumen prosedur internal)
    └──▶ REGULASI      (dokumen hukum eksternal)
```

### R-02 — Akta Legal Chain

```
SKMHT ──precedes──▶ APHT ──cancelled_by──▶ ROYA
                     │
                     └──references──▶ SERTIFIKAT

APJB ──precedes──▶ AJB ──references──▶ SERTIFIKAT

PERJANJIAN_KREDIT ──collateral──▶ FIDUSIA
```

Reasoning: Relasi legal ini penting untuk "related document search". Ketika user
melihat satu APHT, sistem harus bisa menunjukkan SKMHT yang mendahuluinya dan
ROYA yang membatalkannya jika ada.

### R-03 — Party Relationships

```
AKTA ──has_party──▶ CLIENT  (N:M — satu akta bisa multi pihak)
AKTA ──signed_by──▶ NOTARIS (N:1)
AKTA ──issued_by──▶ PPAT    (N:1, nullable — khusus akta tanah)
CLIENT ──has_akta──▶ AKTA   (1:N riwayat per client)
```

### R-04 — RAG Pipeline Chain

```
DOKUMEN_LEGAL
    │
    ▼ [OCR Pipeline]
OCR_RESULT (raw text per halaman)
    │
    ▼ [Chunking]
DOC_CHUNK (text segment + posisi)
    │
    ▼ [Embedding: bge-m3]
EMBEDDING_METADATA (vector id, model, dimensi)
    │
    ▼ [Stored in Qdrant]
[Vector Point: chunk_id + payload metadata]
```

### R-05 — Query to Answer Chain

```
USER_QUERY (natural language)
    │
    ▼ [Query Embedding]
QUERY_VECTOR
    │
    ▼ [Qdrant ANN Search + Filter]
TOP_K_CHUNKS  (dengan reranker)
    │
    ▼ [Context Assembly]
PROMPT_CONTEXT (chunk text + source info)
    │
    ▼ [Local LLM]
AI_RESPONSE
    │
    ├──▶ CITATION (chunk_id, page, dokumen)
    └──▶ AUDIT_LOG (query, user, timestamp, chunks_used)
```

### R-06 — Document-Tag-Category

```
DOKUMEN_LEGAL ──has_tag──▶ SEMANTIC_TAG (N:M)
DOKUMEN_LEGAL ──has_category──▶ KATEGORI_HUKUM (N:M)
DOKUMEN_LEGAL ──has_classification──▶ KLASIFIKASI_KERAHASIAAN (N:1)
```

---

## DETAILED FLOW ANALYSIS

### F-A: Document Lifecycle

```
[1] INGESTION
    User upload file (PDF / scan / image / DOCX)
    └── Sistem simpan ke object storage
    └── Buat record DOKUMEN_LEGAL (status: PENDING_OCR)

[2] OCR EXTRACTION
    File → OCR engine (per halaman)
    └── Output: raw text per halaman → simpan ke OCR_RESULT
    └── Confidence score per halaman dicatat
    └── Update DOKUMEN_LEGAL (status: PENDING_EXTRACTION)

[3] METADATA EXTRACTION
    OCR text → NER (Named Entity Recognition)
    └── Ekstrak: NOMOR_AKTA, nama para pihak, tanggal, nomor sertifikat
    └── Simpan ke LEGAL_ENTITY_EXTRACT
    └── Map ke AKTA / CLIENT / SERTIFIKAT jika matching
    └── Update DOKUMEN_LEGAL (status: PENDING_CLASSIFICATION)

[4] CLASSIFICATION & TAGGING
    Dokumen diklasifikasikan:
    └── JENIS_DOKUMEN (Akta APHT? SOP? Regulasi?)
    └── KATEGORI_HUKUM (Hak Tanggungan? Peralihan Hak?)
    └── KLASIFIKASI_KERAHASIAAN (CONFIDENTIAL?)
    └── SEMANTIC_TAG dilekatkan
    └── Update DOKUMEN_LEGAL (status: PENDING_EMBEDDING)

[5] CHUNKING
    OCR text dipotong menjadi DOC_CHUNK:
    └── Strategi: per pasal / per halaman dengan overlap 20%
    └── Setiap chunk menyimpan: posisi, page_number, parent_doc_id
    └── Chunk size optimal: 512–1024 token (bge-m3 context)

[6] EMBEDDING
    Setiap DOC_CHUNK → bge-m3 → vector 1024-dim
    └── Simpan vector ke Qdrant dengan payload metadata
    └── Simpan EMBEDDING_METADATA ke relational DB
    └── Update DOKUMEN_LEGAL (status: ACTIVE)

[7] INDEXING COMPLETE
    Dokumen siap untuk retrieval
    └── Tersedia di: keyword search, semantic search, AI assistant
```

### F-B: OCR Extraction Flow (Detail)

```
Input: File (PDF / Image)
    │
    ▼
[Pre-processing]
    Deskew, denoise, enhance contrast
    Deteksi orientasi halaman
    │
    ▼
[Page Segmentation]
    Split PDF ke halaman individual
    Identifikasi layout (tabel, paragraf, header)
    │
    ▼
[OCR Engine]
    Per-halaman text extraction
    Output: raw_text + confidence_score + bounding_box
    │
    ▼
[Post-processing]
    Koreksi karakter OCR common errors (0→O, 1→I)
    Normalisasi whitespace dan encoding
    Deteksi dan tandai tabel vs paragraf
    │
    ▼
[Legal NER]
    Deteksi entitas:
    - NOMOR_AKTA  → pattern: "No. XXX/Akta/..."
    - TANGGAL     → pattern: tanggal Indonesia (dd MMMM yyyy)
    - NAMA_PIHAK  → NER model / regex
    - NOMOR_SERTIFIKAT → pattern: "SHM/SHGB No. XXX"
    - NOMOR_NIK   → pattern: 16 digit
    - NILAI_JAMINAN → pattern: Rp angka
    │
    ▼
Output: OCR_RESULT record + LEGAL_ENTITY_EXTRACT records
```

### F-C: Semantic Retrieval Flow

```
User Input: pertanyaan natural language
    │
    ▼
[Query Analysis]
    Intent detection: apakah search dokumen, cari akta, tanya hukum?
    Query rewriting jika perlu (expand abbreviasi legal)
    │
    ▼
[Query Embedding]
    Query → bge-m3 → query_vector (1024-dim)
    │
    ▼
[Hybrid Search]
    ├── Semantic search: ANN di Qdrant (cosine similarity)
    │   Filter opsional: jenis_dokumen, tanggal, klasifikasi
    └── Keyword search: BM25 / full-text di DB metadata
    │
    ▼
[Result Fusion]
    Gabung hasil semantic + keyword (Reciprocal Rank Fusion)
    TOP-K candidates (misal: K=20)
    │
    ▼
[Reranking]
    Cross-encoder reranker: re-score TOP-K vs query
    Pilih TOP-N final (misal: N=5)
    │
    ▼
[Context Assembly]
    Gabungkan teks chunk yang terpilih
    Tambahkan source metadata per chunk
    Susun prompt template
    │
    ▼
Output: ranked chunks + source citations
```

### F-D: RAG Architecture (Component View)

```
┌─────────────────────────────────────────────────────────┐
│                    INGESTION PIPELINE                    │
│                                                         │
│  [File Upload] → [OCR] → [NER] → [Chunker] → [Embedder]│
│       │                                          │      │
│  [Object Store]                            [Qdrant]     │
│       │                                          │      │
│  [Metadata DB]  ←────────────────────────────────      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    RETRIEVAL PIPELINE                    │
│                                                         │
│  [Query] → [Embed] → [Qdrant Search] → [Reranker]      │
│                           │                             │
│                    [Metadata DB]                        │
│                    (filter + enrich)                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                   GENERATION PIPELINE                    │
│                                                         │
│  [Chunks] → [Prompt Builder] → [Local LLM] → [Response]│
│                                    │                    │
│                             [Citation Extractor]        │
│                             [Audit Logger]              │
└─────────────────────────────────────────────────────────┘
```

### F-E: Vector Metadata Structure (Qdrant Payload)

Setiap point di Qdrant mewakili satu **DOC_CHUNK** dengan payload berikut:

```
{
  "chunk_id"            : "uuid",           -- primary key chunk
  "doc_id"              : "uuid",           -- parent DOKUMEN_LEGAL
  "nomor_akta"          : "string|null",    -- jika dokumen adalah Akta
  "jenis_dokumen"       : "string",         -- AKTA / SOP / REGULASI / PERJANJIAN
  "jenis_akta"          : "string|null",    -- APHT / SKMHT / FIDUSIA / dll
  "kategori_hukum"      : "string",         -- Hak Tanggungan / Peralihan Hak / dll
  "klasifikasi"         : "string",         -- INTERNAL / CONFIDENTIAL / dll
  "notary_name"         : "string|null",    -- nama notaris dari OCR
  "client_names"        : ["string"],       -- nama para pihak (array)
  "certificate_number"  : "string|null",    -- nomor sertifikat direferensikan
  "tanggal_dokumen"     : "date|null",      -- tanggal akta / dokumen
  "page_number"         : "integer",        -- halaman asal chunk
  "chunk_index"         : "integer",        -- urutan chunk dalam dokumen
  "chunk_text"          : "string",         -- teks aktual (untuk display)
  "source_filename"     : "string",         -- nama file asli
  "ocr_confidence"      : "float|null",     -- rata-rata confidence OCR halaman
  "tags"                : ["string"],       -- semantic tags
  "created_at"          : "datetime",       -- waktu indexing
  "model_version"       : "string"          -- versi embedding model (bge-m3 vX)
}
```

Reasoning: Field `jenis_dokumen`, `jenis_akta`, dan `klasifikasi` wajib ada sebagai
Qdrant filter fields agar retrieval bisa dibatasi secara akurat tanpa menarik
dokumen yang tidak relevan atau tidak boleh diakses user.

### F-F: Document Relationship Model

```
DOKUMEN_LEGAL ──SUPERSEDES──▶ DOKUMEN_LEGAL  (dokumen baru menggantikan lama)
DOKUMEN_LEGAL ──REFERENCED_BY──▶ DOKUMEN_LEGAL (dokumen direferensikan dokumen lain)
DOKUMEN_LEGAL ──CANCELS──▶ DOKUMEN_LEGAL      (Roya membatalkan APHT)
DOKUMEN_LEGAL ──PRECEDES──▶ DOKUMEN_LEGAL     (SKMHT mendahului APHT)
DOKUMEN_LEGAL ──ATTACHMENT_OF──▶ DOKUMEN_LEGAL (lampiran dokumen utama)
```

Tabel relasi: `DOC_RELATIONSHIP`
- `DOC_ID_FROM`
- `DOC_ID_TO`
- `RELATION_TYPE` (SUPERSEDES / CANCELS / PRECEDES / REFERENCED_BY / ATTACHMENT_OF)
- `CREATED_AT`
- `CREATED_BY`

### F-G: AI Assistant Flow

```
[1] User input query (chat interface)

[2] Intent Classification
    ├── SEARCH_DOCUMENT  → cari dokumen tertentu (by nomor, nama, tanggal)
    ├── ASK_LEGAL        → tanya konten hukum dari dokumen
    ├── EXPLAIN_TERM     → jelaskan istilah legal
    ├── RELATED_DOCS     → cari dokumen terkait
    └── SUMMARIZE        → minta ringkasan dokumen

[3] Query Routing
    ├── SEARCH_DOCUMENT → metadata DB query (structured)
    ├── ASK_LEGAL       → RAG pipeline (semantic + generation)
    ├── EXPLAIN_TERM    → knowledge base / LLM direct
    ├── RELATED_DOCS    → DOC_RELATIONSHIP lookup + semantic search
    └── SUMMARIZE       → retrieve full doc chunks → LLM summarize

[4] Retrieval (jika RAG)
    TOP-N chunks dari Qdrant (lihat F-C)

[5] Generation
    Prompt: [System: kamu adalah asisten notaris...] +
            [Context: chunk1, chunk2, ...] +
            [User: {query}]
    LLM → jawaban dalam Bahasa Indonesia

[6] Response Assembly
    ├── Teks jawaban
    ├── Sumber: [{doc_id, nomor_akta, halaman, snippet}]
    ├── Confidence indicator
    └── Saran follow-up query

[7] Audit Logging
    Simpan ke AUDIT_LOG:
    - user_id, session_id, query_id
    - query_text, intent, timestamp
    - chunks_retrieved (chunk_id array)
    - response_id
    - latency_ms
```

---

## METADATA ARCHITECTURE

### Layer 1 — Document Metadata (Relational DB)

Menyimpan informasi struktural tentang dokumen:
- Identitas dokumen (DOC_ID, filename, upload_date, status)
- Klasifikasi (jenis, kategori, kerahasiaan)
- Referensi legal (NOMOR_AKTA, CERTIFICATE_NUMBER)
- Relasi antar dokumen (DOC_RELATIONSHIP)

### Layer 2 — Extraction Metadata (Relational DB)

Menyimpan hasil OCR dan NER:
- OCR_RESULT: raw text per halaman + confidence
- LEGAL_ENTITY_EXTRACT: entitas yang ditemukan + posisi
- Mapping ke entitas master (CLIENT, NOTARIS)

### Layer 3 — Chunk Metadata (Relational DB + Qdrant Payload)

Menyimpan informasi chunk untuk RAG:
- DOC_CHUNK: posisi, ukuran, page, parent_doc
- EMBEDDING_METADATA: model version, embedding id, dimensi
- Disinkronkan ke Qdrant payload untuk filtering efisien

### Layer 4 — Interaction Metadata (Relational DB)

Menyimpan riwayat interaksi pengguna:
- SEARCH_SESSION: sesi pencarian
- AI_QUERY: pertanyaan yang diajukan
- AI_RESPONSE: jawaban yang diberikan
- CITATION: referensi yang digunakan
- AUDIT_LOG: setiap akses dokumen

---

## RECOMMENDATION

### REC-01 — Database Strategy
Gunakan **PostgreSQL** (bukan Oracle) untuk metadata layer mengingat:
- Lebih native untuk teks dan JSON (JSONB untuk entity extract)
- Full-text search built-in (pg_trgm, ts_vector) untuk hybrid search
- Lebih ringan dan OSS-friendly untuk deployment internal

Gunakan Oracle hanya jika ada integrasi wajib dengan sistem legacy kantor.

### REC-02 — Chunking Strategy
Implementasikan **semantic chunking** berbasis struktur legal:
1. Deteksi heading: "Pasal X", "Ayat X", "Klausul X"
2. Split per unit struktural tersebut
3. Gunakan overlap 20% antar chunk untuk jaga kontinuitas konteks
4. Max chunk size: 1024 token (sesuai bge-m3 context window)

Jangan gunakan fixed-size chunking — akan memotong klausul hukum di tengah.

### REC-03 — Embedding Model
**bge-m3** sudah tepat — model ini mendukung Bahasa Indonesia dan multi-lingual.
Pertimbangkan fine-tuning pada corpus hukum Indonesia jika tersedia (UU, PP, akta)
untuk meningkatkan kualitas retrieval terminologi spesifik.

### REC-04 — OCR Engine Priority
Evaluasi engine berdasarkan akurasi pada teks hukum Indonesia:
1. **PaddleOCR** — akurasi tinggi, support Bahasa Indonesia, OSS
2. **Tesseract 5** — fallback, lebih ringan
3. **Cloud OCR** (Google/AWS) — pilihan jika akurasi lokal tidak cukup

Post-processing wajib untuk koreksi kesalahan umum OCR pada dokumen notaris
(angka 0/O, tanda tangan memotong teks, watermark mengganggu).

### REC-05 — Hybrid Search Implementation
Implementasikan **Reciprocal Rank Fusion (RRF)** untuk menggabungkan:
- Qdrant semantic search hasil
- PostgreSQL full-text search hasil

RRF lebih stabil dari weighted sum dan tidak membutuhkan tuning weight.

### REC-06 — Access Control per Classification
Terapkan filter Qdrant berdasarkan `klasifikasi` field:
- User role `STAFF` → dapat akses: PUBLIC, INTERNAL
- User role `NOTARIS` → dapat akses: + CONFIDENTIAL
- User role `PIMPINAN` → dapat akses: semua termasuk STRICTLY_CONFIDENTIAL

Filter ini harus diterapkan di level retrieval (Qdrant filter), bukan hanya di
level UI, untuk mencegah data leak via prompt injection.

### REC-07 — Ambiguity: Oracle vs PostgreSQL
Konteks awal menyebut Oracle 19C. Namun untuk RAG metadata store, PostgreSQL
lebih disarankan. **Keputusan ini perlu konfirmasi** sebelum DDL dibuat.

Pertanyaan untuk approval:
- Apakah ada Oracle license yang sudah tersedia?
- Apakah ada sistem existing yang perlu diintegrasikan via Oracle?
- Jika tidak ada constraint, apakah bisa switch ke PostgreSQL?

---

## STATUS

```
STEP 1 — ANALYZE NEW DOMAIN   ✅ COMPLETE
STEP 2 — DDL Design           ⏸ WAITING APPROVAL
STEP 3 — API Design           ⏸ WAITING APPROVAL
STEP 4 — Frontend Screens     ⏸ WAITING APPROVAL
STEP 5 — RAG Pipeline Code    ⏸ WAITING APPROVAL
```

**Menunggu konfirmasi sebelum lanjut ke STEP 2.**

---

*Generated by: NOTARIST RAG PLATFORM — ANALYSIS_FIRST mode*  
*File: /generated/docs/rag_architecture_analysis.md*  
*Date: 2026-05-23*
