# STEP 3 — INGESTION & RETRIEVAL ARCHITECTURE
# NOTARIST RAG PLATFORM — LEGAL DOCUMENT INTELLIGENCE

**Version:** v1.0
**Date:** 2026-05-23
**Mode:** ANALYSIS_FIRST
**Status:** DRAFT — Pending Approval
**Scope:** Full pipeline architecture, security flow, retrieval design, lifecycle management

---

## SUMMARY

Dokumen ini mendefinisikan arsitektur lengkap untuk **semua pipeline** dalam
NOTARIST RAG Platform: dari dokumen masuk hingga jawaban AI ter-cite dan
ter-audit. Tidak ada implementasi code — murni architecture blueprint.

**Confirmed Architecture Decisions yang berlaku di STEP 3:**

| Decision | Keputusan |
|---|---|
| Oracle schemas | NOTARIST + NOTARIST_STG + NOTARIST_SEC |
| Officer model | PERSON_MASTER → NOTARIS_MASTER, PPAT_MASTER |
| Document entities | SOP_MASTER + REGULASI_MASTER sebagai entity khusus |
| Encryption | TDE-compatible + application-layer masking |
| RBAC | Hybrid: Spring Boot + Oracle VPD + Qdrant filter |
| Retrieval | Hybrid: Qdrant semantic + PostgreSQL BM25 + RRF fusion |
| Citation | Citation-first response architecture |

---

## FINDINGS

### F-01 — Pipeline Harus Idempotent
Setiap tahap pipeline (OCR, NER, Chunking, Embedding) harus idempotent —
dapat diulang tanpa menghasilkan data duplikat. Idempotency key: `(doc_id, stage)`.
Ini penting untuk recovery dari failure dan re-indexing.

### F-02 — Staging Schema sebagai Buffer Keamanan
`NOTARIST_STG` bukan hanya staging tabel — ini adalah **security buffer**.
Dokumen baru masuk ke STG terlebih dahulu, menjalani validasi dan malware scan,
sebelum dipromosikan ke `NOTARIST.DOC_MASTER`. Ini mencegah pollusi data master.

### F-03 — OCR Quality Menentukan RAG Quality
Kualitas OCR adalah bottleneck utama seluruh sistem. Jika OCR menghasilkan
karakter salah pada `NOMOR_AKTA`, semua downstream akan salah: NER gagal
map ke master, embedding mewakili teks yang salah, retrieval tidak relevan.
OCR quality gate adalah tahap paling kritis.

### F-04 — Encrypted Fields Tidak Boleh Masuk Chunk
Field sensitif (NIK, NILAI_TRANSAKSI, ALAMAT, NO_TELP, EMAIL) yang
dienkripsi di Oracle **tidak boleh** muncul dalam chunk text yang diembedding.
Jika field ini muncul dalam OCR result, wajib di-redact/mask sebelum masuk ke
`doc_chunk.chunk_text` dan Qdrant payload. Ini mencegah data sensitif
terekspos via semantic search.

### F-05 — Tiga Jenis Chunking untuk Tiga Jenis Dokumen
- **AKTA**: chunking berbasis klausul hukum (boundary pada kata "Pasal", "Bahwa", penanda notarial)
- **REGULASI**: chunking hierarki (BAB → Pasal → Ayat → Huruf)
- **SOP**: chunking berbasis prosedural step (boundary pada nomor langkah)

Single chunking strategy akan degradasi kualitas retrieval signifikan.

### F-06 — Relationship Graph adalah Fitur Retrieval, Bukan Hanya Data
`DOC_RELATIONSHIP` di Oracle bukan hanya linkage data — ini adalah **retrieval
expansion graph**. Ketika user mencari satu APHT, sistem harus otomatis
menambahkan SKMHT terkait ke context window. Graph traversal memperkaya
RAG context tanpa perlu query tambahan dari user.

### F-07 — Qdrant Payload Harus Minimal tapi Sufficient untuk Filtering
Setiap field dalam Qdrant payload yang di-index memakan memory HNSW graph.
Hanya field yang digunakan untuk **filtering atau display** yang boleh masuk
payload. Field yang hanya relevan di metadata DB (Oracle/PostgreSQL) tidak perlu
di-payload.

### F-08 — Citation Chain adalah Audit Requirement
Setiap jawaban AI harus dapat di-trace ke: response → citation → chunk →
ocr_page → source_file. Ini bukan hanya UX feature — ini adalah **audit dan
compliance requirement** untuk kantor notaris. Jawaban AI tanpa traceability
tidak boleh ditampilkan ke user.

### F-09 — REGULASI Memerlukan Hierarki Pasal sebagai Entity
Berbeda dengan AKTA yang dichunk per klausul, REGULASI memiliki struktur
hierarki resmi (BAB/Pasal/Ayat/Huruf) yang harus dimodelkan sebagai entity
terpisah (`REGULASI_PASAL`). Ini memungkinkan citation pada level "Pasal 14
ayat 2 UU No. 4/1996".

### F-10 — Re-indexing Harus Selektif, Bukan Full Rebuild
Full re-indexing pada ribuan dokumen akan memakan waktu lama dan memblokir
retrieval. Strategi: track `embedding_metadata.model_version` → saat model
baru di-deploy, hanya re-index dokumen dengan `model_version < current_version`.

---

## PIPELINE FLOW

---

### PIPELINE-01: Document Ingestion Flow

Ingestion dimulai dari upload dan berakhir ketika `DOC_MASTER.STATUS = PENDING_OCR`.

```
[USER / SYSTEM]
     │
     │  file upload (PDF / TIFF / DOCX / JPG)
     ▼
[NOTARIST_STG.STG_DOC_INCOMING]
     │
     ├── VALIDASI TEKNIS
     │     ├── File type whitelist: PDF, TIFF, JPEG, PNG, DOCX
     │     ├── Max file size: 100 MB
     │     ├── SHA-256 fingerprint → cek duplikat ke DOC_MASTER.CHECKSUM_SHA256
     │     └── Malware scan (ClamAV atau kompatibel)
     │
     ├── VALIDASI BISNIS
     │     ├── Apakah user punya hak upload?  (RBAC Spring Boot)
     │     ├── Apakah klasifikasi di-assign?
     │     └── Apakah jenis dokumen valid?
     │
     ├── [GAGAL] → STG_DOC_INCOMING.STATUS = REJECTED
     │               + NOTARIST_SEC.AUDIT_TRAIL (action: UPLOAD_REJECTED)
     │               + return error response ke user
     │
     └── [LULUS] → Promosi ke NOTARIST schema
                    ├── NOTARIST.DOC_MASTER INSERT (STATUS: PENDING_OCR)
                    ├── NOTARIST.DOC_VERSION INSERT (VERSION: 1, TYPE: INITIAL)
                    ├── NOTARIST_SEC.USER_DOC_ACCESS INSERT (TYPE: UPLOAD)
                    ├── NOTARIST_SEC.AUDIT_TRAIL INSERT
                    ├── PostgreSQL: rag.doc_processing_log INSERT (STAGE: UPLOAD, STATUS: COMPLETED)
                    └── STG_DOC_INCOMING.STATUS = PROMOTED
```

**Idempotency Key:** `(CHECKSUM_SHA256)` → dokumen identik tidak diproses dua kali.

---

### PIPELINE-02: OCR Pipeline Architecture

Input: `DOC_MASTER.STATUS = PENDING_OCR`
Output: `rag.ocr_result` rows per halaman, `DOC_MASTER.STATUS = PENDING_EXTRACTION`

```
[OCR WORKER] picks doc from queue WHERE STATUS = PENDING_OCR
     │
     ▼
[PRE-PROCESSING per file]
     ├── Convert ke format terstandar (grayscale, 300 DPI minimum)
     ├── Deskew: koreksi kemiringan scan
     ├── Denoise: hilangkan noise dari scan
     ├── Enhance contrast: perkuat teks vs background
     └── Detect halaman: split multi-page PDF → per-page images
     │
     ▼
[LAYOUT ANALYSIS per halaman]
     ├── Deteksi region: TEXT | TABLE | IMAGE | SIGNATURE | HEADER_FOOTER
     ├── Bounding box per region
     └── Flag: has_table, has_image, has_handwriting
     │
     ▼
[OCR EXTRACTION per region]
     │
     ├── Engine PRIMARY: PaddleOCR
     │     └── Model: PP-OCRv4 (Indonesian + Latin support)
     │
     ├── [Jika confidence < 0.70] → Engine FALLBACK: Tesseract 5
     │     └── Lang: ind+eng
     │
     └── [Jika masih confidence < 0.60] → FLAG_FOR_MANUAL_REVIEW
     │
     ▼
[POST-PROCESSING]
     ├── Karakter koreksi OCR errors:
     │     '0' ↔ 'O', '1' ↔ 'l', '|' ↔ 'I' (dalam konteks angka vs huruf)
     ├── Whitespace normalisasi
     ├── Encoding normalisasi → UTF-8
     ├── Deteksi dan preserve struktur tabel (row/col format)
     └── Strip watermark text jika terdeteksi sebagai noise
     │
     ▼
[SENSITIVE FIELD REDACTION — sebelum simpan ke PostgreSQL]
     ├── Deteksi pola NIK: 16 digit
     ├── Deteksi pola currency: Rp [angka]
     ├── Deteksi pola telp/email
     └── Redact → ganti dengan [REDACTED_NIK], [REDACTED_AMOUNT], dll
         (untuk chunk_text; raw_text tetap utuh di ocr_result)
     │
     ▼
[STORE]
     ├── PostgreSQL: rag.ocr_result INSERT (satu row per halaman)
     │     raw_text = original OCR output (sebelum redaksi, dilindungi akses)
     │     cleaned_text = versi post-processed (siap chunking)
     │     confidence_score = rata-rata per halaman
     │
     ├── Oracle: DOC_MASTER.PAGE_COUNT UPDATE
     ├── Oracle: DOC_MASTER.STATUS UPDATE → PENDING_EXTRACTION
     └── PostgreSQL: rag.doc_processing_log INSERT (STAGE: OCR, STATUS: COMPLETED)

[OCR QUALITY GATES]
     Confidence ≥ 0.85  → AUTO APPROVED → lanjut NER
     Confidence 0.70-0.84 → FLAGGED → lanjut NER tapi tandai untuk review
     Confidence < 0.70  → MANUAL REVIEW REQUIRED → notif admin, pipeline pause
```

**Catatan OCR untuk dokumen hukum Indonesia:**
- Tantangan umum: materai, cap basah, tanda tangan memotong teks
- Solusi: multi-region OCR, cap/tanda tangan di-detect sebagai IMAGE region
- Dokumen lama (pre-2000) perlu enhance yang lebih agresif

---

### PIPELINE-03: Metadata Extraction Pipeline (NER)

Input: `rag.ocr_result` (cleaned_text per halaman)
Output: `rag.legal_entity_extract`, update `NOTARIST.AKTA_MASTER` dan related tables

```
[NER WORKER] picks doc WHERE STATUS = PENDING_EXTRACTION
     │
     ▼
[PHASE 1: RULE-BASED EXTRACTION]
Pattern matching untuk entitas berstruktur tinggi:

     NOMOR_AKTA:
         Pattern: "Nomor (\d+[/\-]\w+[/\-]\d{4})"
         Alias: "No.", "Akta No.", "Nomor Akta"

     TANGGAL:
         Pattern: dd [nama_bulan_indo] yyyy
         Normalize → ISO 8601

     NOMOR_SERTIFIKAT:
         Pattern: "(SHM|SHGB|SHP|HGB|HP)[/\s]No[./\s](\w+)"

     NIK:
         Pattern: \b\d{16}\b
         → Redact in chunk_text, store only presence flag + masked version

     NILAI_TRANSAKSI:
         Pattern: Rp\.?\s*[\d.,]+ (juta|miliar)?
         → Redact in chunk_text, store amount only in Oracle (encrypted)

     NPWP:
         Pattern: \d{2}\.\d{3}\.\d{3}\.\d{1}-\d{3}\.\d{3}

     │
     ▼
[PHASE 2: NER MODEL EXTRACTION]
Model-based untuk entitas yang tidak berstruktur:

     NAMA_PIHAK:
         NER model (Indonesian NER) → detect PER entities
         Context window: cari dekat "Tuan", "Nyonya", "Perseroan", "PT."

     PERAN_PIHAK:
         Keyword matching: "Pihak Pertama", "Pihak Kedua",
         "selanjutnya disebut PEMBELI", "bertindak sebagai DEBITUR"
         Pattern: [nama] selanjutnya disebut [peran]

     NAMA_NOTARIS:
         Pattern dekat: "dihadapan saya", "Notaris di", "berkantor di"

     NAMA_PPAT:
         Pattern dekat: "PPAT", "Pejabat Pembuat Akta Tanah"

     LOKASI:
         NER LOC entities + pattern: kabupaten, kota, kecamatan, kelurahan
     │
     ▼
[PHASE 3: MAPPING KE ORACLE MASTER DATA]

     Untuk setiap entitas yang diekstrak:

     NOTARY_NAME → fuzzy match ke NOTARIST.NOTARIS_MASTER
          ├── Match score ≥ 0.90 → mapped_to_oracle = TRUE, oracle_record_id = NOTARY_ID
          ├── Match score 0.70-0.89 → FLAG untuk konfirmasi manual
          └── Match score < 0.70 → Tidak di-map, ditandai UNMATCHED

     CLIENT_NAME → fuzzy match ke NOTARIST.CLIENT_MASTER
          ├── Match ≥ 0.90 → mapped, create AKTA_PIHAK record
          ├── Match 0.70-0.89 → flag untuk review
          └── No match → trigger optional CREATE CLIENT (approval workflow)

     NOMOR_SERTIFIKAT → exact match ke NOTARIST.SERTIFIKAT_MASTER
          ├── Match → create AKTA_SERTIFIKAT record
          └── No match → orphan reference, logged

     │
     ▼
[STORE]
     ├── PostgreSQL: rag.legal_entity_extract INSERT (setiap entitas)
     ├── Oracle:     AKTA_MASTER INSERT (jika dokumen = AKTA + NOMOR_AKTA ditemukan)
     ├── Oracle:     AKTA_PIHAK INSERT (per pihak yang teridentifikasi)
     ├── Oracle:     AKTA_SERTIFIKAT INSERT (jika sertifikat direferensikan)
     ├── Oracle:     DOC_MASTER.STATUS UPDATE → PENDING_EMBEDDING
     └── PostgreSQL: rag.doc_processing_log INSERT (STAGE: NER, STATUS: COMPLETED)
```

---

### PIPELINE-04: Semantic Tagging Flow

Input: `rag.legal_entity_extract` + `rag.ocr_result.cleaned_text`
Output: `NOTARIST.DOC_TAG_MAP`, update `rag.semantic_metadata`

```
[CLASSIFICATION MODEL / RULE ENGINE]
     │
     ├── DOCUMENT TYPE CLASSIFICATION
     │     Input: first 2 halaman OCR text + filename
     │     Output: JENIS_DOKUMEN ∈ {AKTA, SOP, REGULASI, PERJANJIAN, LAIN}
     │     Trigger: keyword presence + structural pattern
     │     Confidence threshold: ≥ 0.80 auto, < 0.80 manual review
     │
     ├── JENIS AKTA CLASSIFICATION (jika JENIS_DOKUMEN = AKTA)
     │     Hierarchy:
     │     Hak Tanggungan → {APHT, SKMHT, ROYA}
     │     Fidusia        → {FIDUSIA}
     │     Peralihan Hak → {AJB, APJB, APH}
     │     Kuasa         → {KUASA, SKMHT}
     │     Wasiat/Waris  → {WASIAT}
     │     Registrasi    → {WAARMERKING, LEGALISASI}
     │
     ├── KERAHASIAAN CLASSIFICATION (auto-assign berdasarkan jenis)
     │     Default rules:
     │     NILAI_TRANSAKSI ada → minimum CONFIDENTIAL
     │     NIK ada → minimum CONFIDENTIAL
     │     Jenis APHT/FIDUSIA → CONFIDENTIAL
     │     Jenis SOP INTERNAL → INTERNAL
     │     Jenis REGULASI → PUBLIC
     │     Override: manual oleh Notaris/Pimpinan
     │
     ├── LEGAL CATEGORY TAGGING
     │     Kategori hukum dari jenis akta:
     │     APHT/SKMHT/ROYA → "Hak Tanggungan"
     │     FIDUSIA → "Jaminan Fidusia"
     │     AJB/APH → "Peralihan Hak Atas Tanah"
     │     WASIAT → "Hukum Waris"
     │
     └── KEYWORD SEMANTIC TAGGING
           LLM-assisted tagging dari teks:
           → "tanah sawah", "sertifikat HGB", "kredit pemilikan rumah", dll.
           Max 10 tag per dokumen
           Stored with confidence_score
     │
     ▼
[STORE]
     ├── Oracle:     DOC_TAG_MAP INSERT (per tag, TAG_SOURCE = 'AI')
     ├── Oracle:     DOC_MASTER.KLASIFIKASI UPDATE (jika auto-assign lebih tinggi)
     ├── PostgreSQL: rag.semantic_metadata INSERT
     │               (jenis_dokumen, jenis_akta, klasifikasi, auto_tags, legal_categories)
     └── PostgreSQL: rag.doc_processing_log UPDATE (STAGE: NER, include tagging)
```

---

### PIPELINE-05: Chunking Strategy

Input: `rag.ocr_result.cleaned_text` per dokumen
Output: `rag.doc_chunk` rows

#### Strategy A — AKTA Chunking

```
PRINSIP: Satu chunk = satu unit makna hukum yang dapat berdiri sendiri

[STEP 1] Deteksi boundary hukum:
     Marker struktural dalam dokumen Akta:
     - Pembuka:   "---Nomor:", "Pada hari ini"
     - Premis:    "---bahwa", "---Bahwa"
     - Komparisi: "Tuan/Nyonya [nama]"
     - Pasal:     "---Pasal [nomor]---"
     - Penutup:   "Demikianlah Akta ini dibuat"
     - Saksi:     "dihadiri saksi-saksi"
     - Tandatangan section

[STEP 2] Split per logical unit:
     unit_1 = IDENTITAS (nomor akta, tanggal, notaris, lokasi)
     unit_2 = KOMPARISI (para pihak)
     unit_3..N = KLAUSUL per pasal/bagian
     unit_N+1 = PENUTUP + SAKSI

[STEP 3] Size control:
     Target: 400-800 token per chunk
     Jika unit < 100 token → merge dengan unit berikutnya
     Jika unit > 1024 token → split di paragraph break

[STEP 4] Overlap injection:
     Setiap chunk menyertakan 1 kalimat terakhir chunk sebelumnya (context bridge)
     Flag: has_overlap_prev = TRUE

[STEP 5] Sensitive field redaction:
     Cek setiap chunk untuk pattern NIK, Rp, telp, email
     → Replace dengan placeholder
```

#### Strategy B — REGULASI Chunking

```
PRINSIP: Chunk mengikuti struktur hierarki resmi regulasi

[STEP 1] Parse hierarchy tree:
     BAB I
     └── Pasal 1
         ├── Ayat (1)
         │   └── Huruf a, b, c
         └── Ayat (2)
     BAB II
     └── Pasal 2 ...

[STEP 2] Chunk per PASAL (unit terkecil yang bermakna):
     chunk = [judul BAB] + [judul Pasal] + [seluruh ayat dalam Pasal]
     Ini memastikan Pasal tidak terpecah antar chunk

[STEP 3] Preserve hierarchy dalam metadata:
     chunk_type = 'ARTICLE'
     regulasi_pasal_ref: {bab: "I", pasal: "14", ayat: null}

[STEP 4] Amendment chunk:
     Jika ada pasal yang diubah oleh regulasi lain →
     chunk memuat versi terbaru + catatan "(diubah oleh [ref])"
```

#### Strategy C — SOP Chunking

```
PRINSIP: Chunk per prosedural step atau sub-section

[STEP 1] Deteksi marker prosedural:
     Angka urut: "1.", "2.", "a.", "b."
     Header section: "Tujuan", "Ruang Lingkup", "Definisi", "Prosedur"
     Sub-step: "1.1", "1.2"

[STEP 2] Chunk per section utama:
     section_1 = Tujuan + Ruang Lingkup + Definisi (satu chunk)
     section_2..N = Per kelompok prosedural step (3-5 step per chunk)

[STEP 3] Preserve SOP context dalam prefix:
     Setiap chunk diawali: "[SOP: {sop_code}] [Tahap: {section_title}]"
     Ini membantu retrieval mengenali konteks SOP
```

**Chunking Config Table:**

| Parameter | AKTA | REGULASI | SOP | PERJANJIAN |
|---|---|---|---|---|
| Strategy | Klausul-based | Hierarchy-based | Step-based | Klausul-based |
| Target tokens | 400-800 | 300-600 | 200-500 | 400-800 |
| Min tokens | 100 | 80 | 50 | 100 |
| Max tokens | 1024 | 800 | 600 | 1024 |
| Overlap | 15% | 0% (strict) | 10% | 15% |
| Chunk type | CLAUSE | ARTICLE | PARAGRAPH | CLAUSE |

---

### PIPELINE-06: Embedding Pipeline

Input: `rag.doc_chunk` WHERE `embedding_status = 'PENDING'`
Output: `rag.embedding_metadata` + Qdrant points

```
[EMBEDDING WORKER] — batch processing

[STEP 1] Fetch pending chunks:
     Query: SELECT chunk_id, doc_id, chunk_text FROM rag.doc_chunk
            WHERE embedding_status = 'PENDING'
            ORDER BY created_at
            LIMIT 100  -- batch size

[STEP 2] Build Qdrant payload per chunk:
     Enrich chunk dengan metadata dari:
     - rag.semantic_metadata (jenis_dokumen, jenis_akta, klasifikasi, tags)
     - Oracle DOC_MASTER (via doc_id reference)
     - Oracle AKTA_MASTER (nomor_akta, notary_id, tanggal_akta)

[STEP 3] Generate embeddings:
     Model: bge-m3
     Input: chunk_text (TANPA sensitive field — sudah di-redact)
     Output: vector float[1024]
     Batch: 32 chunks per inference call (optimal GPU utilization)

[STEP 4] Upsert ke Qdrant:
     Point ID: UUID (baru, jadi qdrant_point_id)
     Vector: float[1024]
     Payload: lihat QDRANT PAYLOAD SCHEMA di bawah
     Collection: notarist_legal_docs

[STEP 5] Update PostgreSQL:
     rag.embedding_metadata INSERT:
         chunk_id, doc_id, qdrant_point_id, model_name, model_version,
         vector_dimension=1024, indexing_status='INDEXED', indexed_at=NOW()
     rag.doc_chunk UPDATE:
         embedding_status = 'INDEXED', is_indexed = TRUE

[STEP 6] Per dokumen — cek apakah semua chunk sudah indexed:
     IF COUNT(chunks) = COUNT(indexed_chunks) THEN:
         Oracle: DOC_MASTER.STATUS UPDATE → 'ACTIVE'
         PostgreSQL: rag.semantic_metadata.is_searchable UPDATE → TRUE
         Qdrant: UPDATE payload.is_searchable = true (per point)
         PostgreSQL: rag.doc_processing_log INSERT (STAGE: INDEXING, STATUS: COMPLETED)

[ERROR HANDLING]
     Qdrant upsert gagal → retry up to 3x dengan exponential backoff
     Masih gagal → rag.doc_chunk.embedding_status = 'FAILED'
                  rag.doc_processing_log INSERT (STATUS: FAILED, error_message)
     Alert admin untuk manual intervention
```

**Qdrant Payload Schema (per vector point):**

```json
{
  "chunk_id":           "<uuid>",
  "doc_id":             "<uuid-string>",
  "jenis_dokumen":      "AKTA | SOP | REGULASI | PERJANJIAN | LAIN",
  "jenis_akta":         "APHT | SKMHT | FIDUSIA | ROYA | AJB | null",
  "klasifikasi":        "PUBLIC | INTERNAL | CONFIDENTIAL | STRICTLY_CONFIDENTIAL",
  "notary_id":          "<uuid-string> | null",
  "nomor_akta":         "<string> | null",
  "tanggal_dokumen":    "YYYY-MM-DD | null",
  "page_number_start":  <integer>,
  "page_number_end":    <integer>,
  "chunk_index":        <integer>,
  "chunk_type":         "CLAUSE | ARTICLE | PARAGRAPH | HEADER | TABLE | LAIN",
  "chunk_text":         "<redacted-safe string>",
  "token_count":        <integer>,
  "source_filename":    "<string>",
  "ocr_confidence":     <float | null>,
  "tags":               ["<string>"],
  "model_name":         "bge-m3",
  "model_version":      "<semver>",
  "indexed_at":         "<ISO 8601>",
  "is_searchable":      <boolean>,
  "is_active":          <boolean>
}
```

**Indexed Payload Fields (Qdrant keyword/range index):**

```
jenis_dokumen   → keyword index
jenis_akta      → keyword index
klasifikasi     → keyword index
doc_id          → keyword index
notary_id       → keyword index
tanggal_dokumen → datetime range index
tags            → keyword[] index
is_searchable   → bool index
is_active       → bool index
```

---

### PIPELINE-07: Hybrid Retrieval Architecture

Setiap query masuk melewati dua retrieval engine yang hasilnya di-fuse.

```
USER QUERY (natural language)
     │
     ▼
[QUERY PREPROCESSING]
     ├── Normalisasi: lowercase, strip whitespace
     ├── Abbreviasi expansion:
     │     "APHT" → "Akta Pemberian Hak Tanggungan"
     │     "SKMHT" → "Surat Kuasa Membebankan Hak Tanggungan"
     │     "HT" → "Hak Tanggungan"
     ├── Spell check untuk typo umum
     └── Language detect (mostly Bahasa Indonesia)
     │
     ▼
[ACCESS FILTER CONSTRUCTION]
     Berdasarkan user.role dari JWT:
     ├── STAFF        → klasifikasi IN ('PUBLIC', 'INTERNAL')
     ├── NOTARIS      → klasifikasi IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL')
     ├── PIMPINAN     → klasifikasi IN (semua)
     └── ADMIN        → klasifikasi IN (semua)

     Additional filter dari request:
     ├── jenis_dokumen (opsional dari UI filter)
     ├── jenis_akta    (opsional dari UI filter)
     └── tanggal_range (opsional dari UI filter)
     │
     ┌─────────────────────────────────────────────────────┐
     │                   PARALLEL SEARCH                   │
     └─────────────────────────────────────────────────────┘
     │                                                     │
     ▼                                                     ▼
[SEMANTIC SEARCH — Qdrant]                  [KEYWORD SEARCH — PostgreSQL]
     │                                                     │
     ├── Embed query via bge-m3               ├── ts_query dari normalized query
     │   query_vector: float[1024]             ├── ts_rank vs chunk_text_tsv (BM25)
     │                                         └── Filter: doc_id IN
     ├── ANN search di Qdrant                      (SELECT doc_id FROM rag.semantic_metadata
     │   limit: TOP_K = 20                          WHERE is_searchable = TRUE)
     ├── Filter: access + user filter              + klassifikasi filter via semantic_metadata
     └── Result: [{chunk_id, score, payload}]   Result: [{chunk_id, rank, snippet}]
     │                                                     │
     └──────────────────┬──────────────────────────────────┘
                        │
                        ▼
            [RRF FUSION — Reciprocal Rank Fusion]
                   score_RRF(chunk) = Σ 1 / (k + rank_i)
                   k = 60 (standard RRF constant)
                   Merge top-20 dari semantic + top-20 dari keyword
                   Deduplicate by chunk_id
                   Result: TOP-30 merged candidates with RRF score
                        │
                        ▼
                [DIVERSITY ENFORCEMENT]
                   Max 3 chunks dari dokumen yang sama
                   Ensures source diversity dalam context window
                        │
                        ▼
                [STORE ke PostgreSQL]
                   rag.retrieval_result INSERT
                   (query_id, chunk_id, semantic_score, keyword_score, final_score)
```

---

### PIPELINE-08: Reranking Flow

Input: TOP-30 candidates dari RRF fusion
Output: TOP-N final chunks untuk context assembly

```
[CROSS-ENCODER RERANKER]

     Input pairs: [(query_text, chunk_text_1), (query_text, chunk_text_2), ...]
     Model: cross-encoder reranker (multilingual, atau fine-tuned pada domain hukum)

     Process:
     ├── Tokenize: [CLS] query [SEP] chunk [SEP]
     ├── Forward pass → relevance score per pair
     └── Scores tidak comparable antar query, hanya digunakan untuk ranking

     Output: 30 chunks dengan rerank_score
                   │
                   ▼
     [SELECT TOP-N]
          N = 5 (default, configurable per intent)
          Intent-based N override:
          ├── ASK_LEGAL      → N = 5 (need rich context)
          ├── SUMMARIZE      → N = 10 (need broader context)
          ├── EXPLAIN_TERM   → N = 3 (targeted answer)
          └── RELATED_DOCS   → N = 7 (need breadth)
                   │
                   ▼
     [UPDATE PostgreSQL]
          rag.retrieval_result UPDATE:
          rerank_score, final_score, was_used_in_context = TRUE (untuk top-N)
```

---

### PIPELINE-09: Citation Architecture

Sistem citation memastikan setiap jawaban AI ter-trace ke sumber spesifik.

```
[CONTEXT ASSEMBLY]

     TOP-N chunks → ordered by rerank_score DESC
     Format per chunk dalam context:
     ┌──────────────────────────────────────────────────┐
     │ [SUMBER-1] {nomor_akta} | Hal. {page} | {jenis} │
     │ {chunk_text}                                     │
     │                                                  │
     │ [SUMBER-2] ...                                   │
     └──────────────────────────────────────────────────┘
                   │
                   ▼
[LLM PROMPT CONSTRUCTION]

     System prompt:
     "Kamu adalah asisten hukum internal kantor notaris.
     Jawab berdasarkan dokumen yang diberikan.
     Jangan mengarang informasi di luar dokumen.
     Selalu kutip nomor sumber [SUMBER-X] saat menyebut informasi spesifik.
     Gunakan bahasa Indonesia yang formal dan akurat secara hukum."

     User prompt:
     "Dokumen referensi:\n{context_assembly}\n\nPertanyaan: {query}"
                   │
                   ▼
[LLM RESPONSE]

     Response format yang diinginkan:
     ┌──────────────────────────────────────────────────────────┐
     │ "Berdasarkan APHT Nomor 45/2024 [SUMBER-1], hak         │
     │ tanggungan tersebut diberikan oleh Tuan ... kepada      │
     │ PT ... dengan nilai jaminan sebagaimana tercantum       │
     │ dalam dokumen [SUMBER-1]. Menurut Pasal 14 UUHT        │
     │ [SUMBER-3], ..."                                        │
     └──────────────────────────────────────────────────────────┘
                   │
                   ▼
[CITATION EXTRACTION]

     Parse response untuk [SUMBER-X] references
     Map [SUMBER-X] → chunk_id, doc_id, page_number
     Verify citation: chunk benar-benar mendukung klaim yang dibuat
     (opsional: LLM self-check untuk hallucination detection)
                   │
                   ▼
[STORE CITATIONS]
     PostgreSQL: rag.ai_response INSERT
     PostgreSQL: rag.citation INSERT per [SUMBER-X]:
          citation_text = teks kalimat yang mengacu sumber ini
          chunk_id      = chunk yang dirujuk
          doc_id        = dokumen asal
          page_number   = halaman dalam dokumen
          citation_order = 1, 2, 3 ...
```

**Citation Display Format (untuk frontend):**

```
ANSWER:
"Berdasarkan APHT No. 45/2024, hak tanggungan diberikan kepada
PT. Bank Maju dengan nilai jaminan sebagaimana tercantum dalam akta."

SOURCES:
[1] APHT No. 45/2024 — Halaman 3
    "...nilai jaminan sebesar..."
    [Lihat Dokumen] [Lihat Halaman]

[2] UU No. 4 Tahun 1996 — Pasal 14
    "Sertifikat hak tanggungan memuat..."
    [Lihat Dokumen] [Lihat Pasal]
```

---

### PIPELINE-10: Document Security Flow

```
LAYER 1 — UPLOAD VALIDATION (Spring Boot)
     ├── JWT authentication check
     ├── Role check: hak upload?
     ├── File validation (type, size, malware)
     └── Staging insert (NOTARIST_STG)

LAYER 2 — DATA CLASSIFICATION (Auto + Manual)
     ├── Auto-classification saat NER selesai
     │     Rule: NILAI_TRANSAKSI || NIK present → minimum CONFIDENTIAL
     ├── Manual override: Notaris/Pimpinan bisa elevate classification
     └── Stored: DOC_MASTER.KLASIFIKASI (Oracle)

LAYER 3 — ORACLE VPD (Row-Level Security)
     Pada view DOC_MASTER_VW (VPD-enabled):
     ├── STAFF        → WHERE KLASIFIKASI IN ('PUBLIC', 'INTERNAL')
     ├── NOTARIS      → WHERE KLASIFIKASI IN ('PUBLIC','INTERNAL','CONFIDENTIAL')
     └── PIMPINAN/ADMIN → no filter (all rows visible)

     VPD policy function attached to:
     - DOC_MASTER
     - AKTA_MASTER
     - NOTARIST_SEC.USER_DOC_ACCESS (read own records only for STAFF)

LAYER 4 — FIELD MASKING (Spring Boot Response)
     Sensitive fields dalam API response:
     ├── NIK             → "****-****-****-****" (untuk STAFF)
     ├── NPWP            → "**.***.***.**-***.***"
     ├── ALAMAT          → hanya kota + provinsi (untuk STAFF)
     ├── NO_TELP         → "****" suffix only
     ├── EMAIL           → "***@domain.com"
     └── NILAI_TRANSAKSI → visible hanya untuk NOTARIS+ roles

LAYER 5 — QDRANT RETRIEVAL FILTER
     Access filter wajib di setiap query:
     {
       "filter": {
         "must": [
           {"key": "klasifikasi", "match": {"any": [allowed_classifications]}},
           {"key": "is_active",   "match": {"value": true}},
           {"key": "is_searchable","match": {"value": true}}
         ]
       }
     }
     Filter ini di-inject oleh backend, TIDAK bisa dioverride oleh user input.

LAYER 6 — CHUNK TEXT REDACTION
     chunk_text (disimpan di doc_chunk + Qdrant payload) wajib bebas dari:
     - NIK
     - Nilai transaksi spesifik
     - Nomor rekening/account
     - Email/telp pribadi
     Redaksi dilakukan di PIPELINE-02 (pre-storage), bukan post-retrieval.

LAYER 7 — AUDIT LOGGING (setiap akses)
     Oracle NOTARIST_SEC.USER_DOC_ACCESS: view, download, print
     PostgreSQL rag.ai_interaction_audit: setiap RAG query + doc yang terekspos
```

---

### PIPELINE-11: AI Assistant Retrieval Flow

Complete end-to-end flow dari user input hingga response.

```
[USER INPUT via Mobile App]
     │
     ▼
[SPRING BOOT API — /api/v1/search/ask]
     │
     ├── JWT validation → extract user_id, role, permissions
     ├── Rate limiting check (prevent abuse)
     └── rag.search_session INSERT (jika sesi baru)
     │
     ▼
[INTENT CLASSIFICATION]
     Input: query_text
     Method: lightweight classifier (keyword rules + optionally LLM)

     Intent → Route:

     SEARCH_DOCUMENT  → Structured metadata query ke Oracle/PostgreSQL
          (user cari "Akta APHT atas nama Budi bulan Maret 2024")
          Tidak perlu RAG pipeline — langsung query DB

     ASK_LEGAL        → Full RAG Pipeline
          (user tanya "apa isi klausul penjualan dalam akta ini?")

     EXPLAIN_TERM     → LLM Direct atau RAG mini (top-1 chunk)
          (user tanya "apa itu SKMHT?")

     RELATED_DOCS     → DOC_RELATIONSHIP traversal + optional RAG
          (user tanya "ada dokumen lain terkait APHT ini?")

     SUMMARIZE        → Full RAG Pipeline dengan N=10
          (user minta "ringkas akta ini")
     │
     ▼
[ACCESS FILTER BUILD] (berdasarkan role dari JWT)
     │
     ▼
[HYBRID SEARCH] — lihat PIPELINE-07
     │
     ▼
[RERANKING] — lihat PIPELINE-08
     │
     ▼
[RELATIONSHIP EXPANSION] — opsional, berdasarkan intent
     Jika intent = RELATED_DOCS atau mode = COMPREHENSIVE:
     ├── Ambil doc_id dari top chunks
     ├── Query DOC_RELATIONSHIP WHERE DOC_ID_FROM IN (doc_ids)
     └── Tambahkan related docs ke candidate pool
     │
     ▼
[CONTEXT ASSEMBLY] — lihat PIPELINE-09
     Max context tokens = 4096 (configurable per LLM)
     Prioritas: chunks dengan highest rerank_score masuk pertama
     Truncate jika melebihi batas
     │
     ▼
[LLM GENERATION]
     Model: local LLM (Mistral / Llama3 / dll)
     Temperature: 0.1 (low — dokumen hukum butuh deterministik)
     System prompt: legal assistant persona
     Anti-hallucination instruction wajib ada di system prompt
     │
     ▼
[CITATION EXTRACTION] — lihat PIPELINE-09
     │
     ▼
[RESPONSE ASSEMBLY]
     {
       "answer": "...",
       "intent": "ASK_LEGAL",
       "sources": [
         {
           "doc_id": "...",
           "nomor_akta": "45/2024",
           "jenis_dokumen": "AKTA",
           "jenis_akta": "APHT",
           "page": 3,
           "snippet": "...",
           "relevance_score": 0.92
         }
       ],
       "confidence": "HIGH | MEDIUM | LOW",
       "follow_up_suggestions": ["..."]
     }
     │
     ▼
[AUDIT LOGGING]
     PostgreSQL: rag.ai_response INSERT
     PostgreSQL: rag.citation INSERT (per sumber)
     Oracle:     NOTARIST_SEC.USER_DOC_ACCESS INSERT (per doc yang terekspos)
     PostgreSQL: rag.ai_interaction_audit INSERT
```

---

### PIPELINE-12: Audit & Traceability Flow

```
TRACEABILITY CHAIN (bottom-up):

     AI Response
     └── rag.ai_response (response_id, query_id, model_name, tokens)
          └── rag.ai_query (query_id, session_id, user_id, query_text)
               └── rag.search_session (session_id, user_id)

     Citation
     └── rag.citation (citation_id, response_id, chunk_id, doc_id, page)
          └── rag.doc_chunk (chunk_id, chunk_text, chunk_index, page_number)
               └── rag.ocr_result (ocr_id, doc_id, page_number, raw_text)
                    └── NOTARIST.DOC_MASTER (doc_id, original_filename, storage_path)

AUDIT EVENTS MATRIX:

     Event                       | Oracle AUDIT_TRAIL | Oracle USER_DOC_ACCESS | PG ai_interaction_audit
     ----------------------------|--------------------|------------------------|-------------------------
     Document upload             | ✓ (INSERT)         |                        |
     Document metadata change    | ✓ (UPDATE)         |                        |
     Document view (raw)         |                    | ✓ (VIEW)               |
     Document download           |                    | ✓ (DOWNLOAD)           |
     Document delete             | ✓ (DELETE)         |                        |
     RAG query executed          |                    |                        | ✓
     Document in RAG response    |                    | ✓ (SYSTEM_ACCESS)      | ✓ (doc_ids_accessed)
     Classification elevated     | ✓ (UPDATE)         |                        |
     User role changed           | ✓ (UPDATE)         |                        |

RETENTION POLICY:
     NOTARIST_SEC.AUDIT_TRAIL     → 10 tahun (sesuai regulasi notaris)
     NOTARIST_SEC.USER_DOC_ACCESS → 5 tahun
     rag.ai_interaction_audit     → 2 tahun (atau sesuai kebijakan internal)
     rag.retrieval_result         → 1 tahun lalu archive
     rag.search_cache             → TTL-based (hours to days)
```

---

### PIPELINE-13: Vector Synchronization Strategy

Qdrant harus selalu sinkron dengan PostgreSQL sebagai source of truth indexing status.

```
SYNC EVENTS:

A. NEW DOCUMENT
   Trigger: DOC_MASTER.STATUS → PENDING_EMBEDDING
   Action:  Embedding worker → upsert points ke Qdrant
            embedding_metadata.indexing_status = 'INDEXED'
            doc_chunk.is_indexed = TRUE

B. DOCUMENT UPDATED (metadata change, tidak ada re-upload)
   Trigger: Perubahan jenis_dokumen, klasifikasi, atau tags
   Action:  Qdrant payload UPDATE (bukan re-embed)
            Hanya update indexed payload fields yang berubah
            embedding_metadata.updated_at = NOW()

C. DOCUMENT DELETED / ARCHIVED
   Trigger: DOC_MASTER.STATUS → DELETED / ARCHIVED
   Action:
   Soft delete (recommended):
   → Update Qdrant payload: is_active = false
   → embedding_metadata.indexing_status = 'DELETED'
   → doc_chunk.is_indexed = FALSE

   Hard delete (scheduled job, weekly):
   → DELETE points dari Qdrant WHERE is_active = false
     AND indexed_at < NOW() - INTERVAL '30 days'

D. DOCUMENT SUPERSEDED (versi baru upload)
   Trigger: DOC_RELATIONSHIP INSERT (type: SUPERSEDES)
   Action:
   → Old doc: Qdrant payload is_active = false
   → New doc: Normal embedding pipeline

E. RE-INDEXING TRIGGER (model update)
   Trigger: New embedding model version deployed
   Action:
   → Identify: embedding_metadata WHERE model_version < current_version
   → Batch re-embed doc_chunk records
   → Qdrant: upsert dengan vector baru (same point_id)
   → Update: embedding_metadata.model_version, indexed_at

CONSISTENCY CHECK (scheduled daily):
   1. Count chunks WHERE is_indexed = TRUE in PostgreSQL
   2. Count matching points WHERE is_active = true in Qdrant
   3. Alert jika divergence > 1%
   4. Log discrepancy ke rag.doc_processing_log
```

---

### PIPELINE-14: Document Lifecycle Architecture

```
STATE MACHINE — DOC_MASTER.STATUS_DOKUMEN:

                     [UPLOAD]
                        │
               [PENDING_OCR] ◄─────── (retry on failure)
                        │
               ┌────────┴────────┐
               │  OCR_RUNNING    │
               └────────┬────────┘
                        │
         ┌──────────────┼──────────────┐
         │  [confidence < 0.60]        │  [confidence ≥ 0.60]
         ▼                             ▼
   [MANUAL_REVIEW]           [PENDING_EXTRACTION]
         │                             │
         │ (operator fixes)    ┌───────┴────────┐
         │                     │  NER_RUNNING   │
         └──────────────────── └───────┬────────┘
                                       │
                              [PENDING_EMBEDDING]
                                       │
                              ┌────────┴────────┐
                              │ EMBEDDING_RUNNING│
                              └────────┬────────┘
                                       │
                                   [ACTIVE] ◄─── Fully searchable
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                   │
               [SUPERSEDED]      [ARCHIVED]          [DELETED]
               (diganti versi)   (non-active)        (soft delete)


TRANSITION RULES:
     PENDING_OCR → OCR_RUNNING          : otomatis (queue)
     OCR_RUNNING → PENDING_EXTRACTION   : jika confidence ≥ 0.60
     OCR_RUNNING → MANUAL_REVIEW        : jika confidence < 0.60
     MANUAL_REVIEW → PENDING_EXTRACTION : operator approve manual OCR
     PENDING_EXTRACTION → PENDING_EMBEDDING : NER selesai
     PENDING_EMBEDDING → ACTIVE         : semua chunks indexed di Qdrant
     ACTIVE → SUPERSEDED                : dokumen baru di-upload sebagai pengganti
     ACTIVE → ARCHIVED                  : manual action oleh Notaris/Admin
     ACTIVE → DELETED                   : soft delete (tidak pernah hard delete akta)

RECOVERY / RETRY POLICY:
     OCR failure:        retry 3x dengan interval 5 menit
     NER failure:        retry 3x (pipeline idempotent)
     Embedding failure:  retry 3x, lalu alert admin
     Qdrant upsert fail: retry 5x dengan exponential backoff
     After max retries:  status = previous_stage (rollback), alert admin
```

---

### PIPELINE-15: Re-indexing Strategy

```
TRIGGER JENIS:

TYPE-A: MODEL UPDATE RE-INDEXING
     Trigger: bge-m3 versi baru di-deploy
     Scope: semua dokumen dengan embedding_metadata.model_version < new_version
     Strategy: batch processing, 500 docs/batch
     Impact: Qdrant points di-upsert (same point_id, new vector)
     Downtime: nol (old vectors tetap aktif selama re-indexing)
     Rollout: gradual — chunk baru pakai model baru, chunk lama update bertahap

TYPE-B: CHUNKING STRATEGY CHANGE RE-INDEXING
     Trigger: chunking algorithm diubah
     Scope: semua dokumen dengan jenis yang terpengaruh
     Strategy:
     1. Set doc_chunk.embedding_status = 'OUTDATED' untuk affected docs
     2. Delete Qdrant points (payload: is_active = false)
     3. Re-chunk dari ocr_result (tidak perlu re-OCR)
     4. Re-embed new chunks
     5. Upsert ke Qdrant dengan new point_ids
     6. Delete old points (hard delete)
     Impact: Temporary coverage gap selama processing

TYPE-C: METADATA CORRECTION RE-INDEXING
     Trigger: Klasifikasi atau tag diubah secara manual
     Scope: satu dokumen
     Strategy:
     1. Query Qdrant: GET all points WHERE doc_id = X
     2. Update payload fields only (no re-embedding needed)
     3. Update rag.semantic_metadata
     4. Update rag.embedding_metadata.updated_at
     Impact: nol — vector tidak berubah, hanya metadata

TYPE-D: DOCUMENT REPLACEMENT RE-INDEXING
     Trigger: Dokumen diupload ulang (file lebih baik, scan ulang)
     Scope: satu dokumen
     Strategy:
     1. Old version: Qdrant is_active = false (soft delete)
     2. New version: full pipeline dari upload → active
     3. DOC_RELATIONSHIP: old SUPERSEDED by new
     4. Old version chunks keep in PostgreSQL (audit trail)
     5. Hard delete old Qdrant points setelah T+30 hari

PRIORITY QUEUE untuk re-indexing:
     HIGH: dokumen ACTIVE yang sering diakses (by access count)
     MEDIUM: dokumen ACTIVE yang jarang diakses
     LOW: dokumen ARCHIVED
```

---

### PIPELINE-16: Legal Document Relationship Graph

```
GRAPH MODEL:

     Nodes: DOC_MASTER records (setiap dokumen)
     Edges: DOC_RELATIONSHIP records (typed, directed)

EDGE TYPES:

     PRECEDES       → Dokumen A mendahului dokumen B secara legal
          SKMHT ──PRECEDES──► APHT
          APJB  ──PRECEDES──► AJB

     CANCELS        → Dokumen A membatalkan/mencabut dokumen B
          ROYA  ──CANCELS──► APHT (roya membatalkan HT)

     SUPERSEDES     → Dokumen A menggantikan dokumen B (revisi/update)
          SOP_v2  ──SUPERSEDES──► SOP_v1
          REGULASI_amend ──SUPERSEDES──► REGULASI_lama

     REFERENCED_BY  → Dokumen A direferensikan dokumen B
          SERTIFIKAT_scan ──REFERENCED_BY──► AJB

     ATTACHMENT_OF  → Dokumen A adalah lampiran dokumen B
          SCAN_KTP ──ATTACHMENT_OF──► AKTA_X

     RELATED_TO     → Hubungan kontekstual (tidak ada hierarki legal)
          FIDUSIA ──RELATED_TO──► PERJANJIAN_KREDIT

GRAPH TRAVERSAL USE CASES:

     UC-1: "Tampilkan semua dokumen terkait APHT ini"
          Traversal: APHT → outgoing edges (semua relasi dari APHT ini)
          + incoming edges (dokumen yang mereferensikan APHT ini)
          Result: SKMHT (precedes), ROYA (cancels), SERTIFIKAT (referenced)

     UC-2: "Dokumen chain untuk sertifikat ini"
          Traversal: SERTIFIKAT_SCAN → REFERENCED_BY → AJB → PRECEDES (check APJB)
          Result: timeline legal sertifikat

     UC-3: "Versi terbaru regulasi ini"
          Traversal: REGULASI_OLD → SUPERSEDES chain → terakhir di chain
          Result: regulasi versi terkini

     UC-4: "Apakah APHT ini sudah di-Roya?"
          Query: DOC_RELATIONSHIP WHERE DOC_ID_FROM = [roya_doc] AND
                 DOC_ID_TO = [apht_doc] AND RELATION_TYPE = 'CANCELS'
          Result: ya/tidak + tanggal roya

GRAPH STORAGE di Oracle:
     NOTARIST.DOC_RELATIONSHIP (adjacency list model)
     Untuk traversal multi-level: gunakan Oracle CONNECT BY atau recursive CTE

RAG INTEGRATION:
     Saat retrieval, jika top chunks berasal dari APHT:
     → Query DOC_RELATIONSHIP untuk dokumen terkait
     → Ambil chunks dari dokumen terkait (SKMHT, ROYA)
     → Tambahkan ke context dengan label "[DOKUMEN TERKAIT: SKMHT No. ...]"
     Ini memberikan LLM konteks yang lebih kaya tanpa user harus tahu
     bahwa perlu mencari dokumen lain.
```

---

## ENTITY RELATIONSHIP

### Entitas Baru dari STEP 3 Architecture Decisions

#### PERSON_MASTER (Oracle — NOTARIST schema)

```
PERSON_MASTER
├── PERSON_ID (PK)
├── FULL_NAME
├── BIRTH_DATE
├── BIRTH_PLACE
├── NIK_ENCRYPTED          ← encrypted via Oracle TDE
├── NPWP_ENCRYPTED         ← encrypted
├── GENDER
├── ALAMAT_ENCRYPTED       ← encrypted
├── NO_TELP_MASKED         ← masked (stored as hash + last 4 digits)
├── EMAIL_MASKED           ← masked
├── STATUS                 ACTIVE | INACTIVE
├── CREATED_AT
└── CREATED_BY

NOTARIS_MASTER
├── NOTARY_ID (PK)
├── PERSON_ID (FK → PERSON_MASTER)  ← new relationship
├── SK_NUMBER
├── SK_DATE
├── WILAYAH_KERJA
├── MASA_JABATAN_MULAI
├── MASA_JABATAN_SELESAI
├── STATUS_LISENSI         AKTIF | NONAKTIF | DICABUT
└── ...

PPAT_MASTER
├── PPAT_ID (PK)
├── PERSON_ID (FK → PERSON_MASTER)  ← new relationship
├── SK_NUMBER
├── WILAYAH_KERJA
├── STATUS_LISENSI
└── ...

OFFICER_ROLE_HISTORY        ← tracking history lisensi
├── HISTORY_ID (PK)
├── PERSON_ID (FK)
├── ROLE_TYPE              NOTARIS | PPAT
├── ROLE_RECORD_ID         (NOTARY_ID atau PPAT_ID)
├── STATUS                 AKTIF | NONAKTIF | DICABUT
├── EFFECTIVE_DATE
├── END_DATE
└── REASON
```

#### REGULASI_MASTER + REGULASI_PASAL (Oracle — NOTARIST schema)

```
REGULASI_MASTER
├── REGULASI_ID (PK)
├── DOC_ID (FK → DOC_MASTER)
├── JENIS_REGULASI         UU | PP | PERPRES | PERMEN | SE | SURAT_EDARAN | LAIN
├── NOMOR_REGULASI         contoh: "4"
├── TAHUN_REGULASI         contoh: 1996
├── JUDUL_REGULASI         contoh: "Undang-Undang Hak Tanggungan"
├── SINGKATAN              contoh: "UUHT"
├── LEMBAGA_PENERBIT       contoh: "DPR RI", "Kemenkumham", "BPN"
├── TANGGAL_DITETAPKAN
├── TANGGAL_DIUNDANGKAN
├── TANGGAL_BERLAKU
├── NOMOR_LEMBARAN_NEGARA
├── PARENT_REGULASI_ID (FK → REGULASI_MASTER, nullable)   ← untuk amendment
├── AMENDMENT_TYPE         REVISI | PENCABUTAN | PENGGANTIAN (nullable)
├── STATUS_REGULASI        BERLAKU | DICABUT | DIUBAH | HISTORIS
└── EXTERNAL_URL           (opsional, link ke JDIH/sumber resmi)

REGULASI_PASAL             ← hierarki pasal (adjacency list)
├── PASAL_ID (PK)
├── REGULASI_ID (FK → REGULASI_MASTER)
├── PARENT_PASAL_ID (FK → REGULASI_PASAL, nullable)
├── LEVEL                  BAB | PASAL | AYAT | HURUF | ANGKA
├── NOMOR                  "I", "1", "(1)", "a"
├── JUDUL                  nullable (BAB biasanya punya judul)
├── KONTEN (CLOB)          teks pasal/ayat
├── URUTAN                 untuk sorting
└── CHUNK_ID_REF           FK → rag.doc_chunk (link ke chunk hasil indexing)
```

#### SOP_MASTER (Oracle — NOTARIST schema)

```
SOP_MASTER
├── SOP_ID (PK)
├── DOC_ID (FK → DOC_MASTER)
├── SOP_CODE               contoh: "SOP-NOT-001"
├── JUDUL
├── TUJUAN (VARCHAR2 2000)
├── RUANG_LINGKUP          VARCHAR2 2000
├── DEPARTMENT_SCOPE       contoh: "NOTARIS", "ADMIN", "SEMUA"
├── VERSION_NUMBER
├── STATUS_SOP             DRAFT | REVIEW | APPROVED | SUPERSEDED | ARCHIVED
├── APPROVER_ID (FK → USER_MASTER)
├── APPROVAL_DATE
├── EFFECTIVE_DATE
├── REVIEW_DATE_NEXT       tanggal review SOP berikutnya
└── PARENT_SOP_ID (FK → SOP_MASTER, nullable)   ← untuk versi baru

SOP_STEP
├── STEP_ID (PK)
├── SOP_ID (FK → SOP_MASTER)
├── STEP_NUMBER
├── STEP_TITLE
├── STEP_DESCRIPTION (CLOB)
├── RESPONSIBLE_ROLE       siapa yang menjalankan step ini
├── EXPECTED_DURATION_MIN  durasi dalam menit (estimasi)
└── CHUNK_ID_REF           FK → rag.doc_chunk
```

### NOTARIST_STG Schema Entities

```
NOTARIST_STG.STG_DOC_INCOMING
├── STG_ID (PK)
├── ORIGINAL_FILENAME
├── FILE_SIZE_BYTES
├── FILE_MIME_TYPE
├── CHECKSUM_SHA256
├── UPLOAD_BY (FK → NOTARIST.USER_MASTER)
├── UPLOAD_TIME
├── VALIDATION_STATUS      PENDING | PASSED | REJECTED
├── VALIDATION_ERRORS      CLOB (JSON array of errors)
├── PROMOTED_DOC_ID        (nullable — set setelah promosi)
└── PROMOTED_AT

NOTARIST_STG.STG_OCR_QUEUE
├── QUEUE_ID (PK)
├── DOC_ID (FK → NOTARIST.DOC_MASTER)
├── PRIORITY               1 (HIGH) | 2 (NORMAL) | 3 (LOW)
├── STATUS                 PENDING | PROCESSING | DONE | FAILED
├── WORKER_ID              ID worker yang memproses
├── QUEUED_AT
├── STARTED_AT
└── COMPLETED_AT
```

### NOTARIST_SEC Schema Entities

```
NOTARIST_SEC.AUDIT_TRAIL
├── AUDIT_ID (PK)
├── SCHEMA_NAME
├── TABLE_NAME
├── RECORD_ID
├── ACTION                 INSERT | UPDATE | DELETE
├── OLD_VALUES (CLOB)      JSON snapshot sebelum
├── NEW_VALUES (CLOB)      JSON snapshot sesudah
├── PERFORMED_BY (FK → NOTARIST.USER_MASTER)
├── PERFORMED_AT
└── IP_ADDRESS

NOTARIST_SEC.USER_DOC_ACCESS
├── ACCESS_ID (PK)
├── USER_ID (FK → NOTARIST.USER_MASTER)
├── DOC_ID (FK → NOTARIST.DOC_MASTER)
├── ACCESS_TYPE            VIEW | DOWNLOAD | EDIT | DELETE | PRINT | SYSTEM_ACCESS
├── ACCESS_TIME
├── IP_ADDRESS
├── USER_AGENT
└── SESSION_REF            (UUID ref ke rag.search_session jika via RAG)

NOTARIST_SEC.SENSITIVE_FIELD_ACCESS_LOG
├── LOG_ID (PK)
├── USER_ID
├── TABLE_NAME
├── RECORD_ID
├── FIELD_NAME             NIK | NILAI_TRANSAKSI | ALAMAT | dll
├── MASKED_OR_CLEAR        MASKED | CLEAR
├── ACCESS_TIME
└── JUSTIFICATION_CODE     (jika CLEAR: kode alasan akses ke data sensitive)
```

---

## SECURITY FLOW

### Encryption Strategy

```
SENSITIVE FIELD CLASSIFICATION:

LEVEL S1 — STRICTLY SENSITIVE (Oracle TDE + Application Encrypt)
     NIK                → kolom _ENCRYPTED suffix, AES-256
     NPWP               → kolom _ENCRYPTED suffix
     NILAI_TRANSAKSI    → kolom _ENCRYPTED suffix (AKTA_MASTER)
     NOMOR_REKENING     → kolom _ENCRYPTED suffix (jika ada)

LEVEL S2 — SENSITIVE (Application Masking)
     ALAMAT             → stored full, display masked per role
     NO_TELP            → stored full, display: "****1234"
     EMAIL              → stored full, display: "***@domain.com"

LEVEL S3 — SEMI-PUBLIC (No Encryption, Role-Based Display)
     NOMOR_AKTA         → visible semua role
     CERTIFICATE_NUMBER → visible semua role
     CLIENT_NAME        → visible, tapi detail (NIK) masked
     TANGGAL_AKTA       → visible semua role

ORACLE TDE SETUP (design, bukan implementasi):
     Tablespace encryption: ENCRYPT USING AES256
     Column-level encryption untuk field S1
     Key management: Oracle Wallet / HSM external

APPLICATION MASKING (Spring Boot):
     @SensitiveField(level = S2, roles = {NOTARIS, PIMPINAN, ADMIN})
     Annotation-driven masking di DTO layer
     Default: semua field S2 ter-mask untuk STAFF

CHUNK TEXT REDACTION RULES:
     NIK pattern:     \b\d{16}\b → [REDACTED_NIK]
     Currency:        Rp\.?\s*[\d,.]+ → [REDACTED_AMOUNT]
     Email pattern:   standard email regex → [REDACTED_EMAIL]
     Phone:           \b(?:\+62|0)[\d\s\-]{8,13}\b → [REDACTED_PHONE]
     Redaksi dilakukan SEBELUM chunk_text disimpan ke PostgreSQL dan Qdrant.
```

### RBAC Matrix

```
RESOURCE              STAFF   NOTARIS  PPAT_OFFICER  PIMPINAN  ADMIN
──────────────────────────────────────────────────────────────────────
Upload dokumen        ✓       ✓        ✓             ✓         ✓
View PUBLIC doc       ✓       ✓        ✓             ✓         ✓
View INTERNAL doc     ✓       ✓        ✓             ✓         ✓
View CONFIDENTIAL     ✗       ✓        ✓             ✓         ✓
View STRICTLY_CONF    ✗       ✗        ✗             ✓         ✓
View NIK (clear)      ✗       ✓        ✓             ✓         ✓
View NILAI (clear)    ✗       ✓        ✓             ✓         ✓
Delete dokumen        ✗       ✗        ✗             ✓         ✓
Change klasifikasi    ✗       ✓        ✓             ✓         ✓
Manage users          ✗       ✗        ✗             ✗         ✓
View audit trail      ✗       ✗        ✗             ✓         ✓
RAG Search (PUBLIC)   ✓       ✓        ✓             ✓         ✓
RAG Search (CONF)     ✗       ✓        ✓             ✓         ✓
Approve SOP           ✗       ✓        ✓             ✓         ✓
```

---

## RETRIEVAL FLOW

### Hybrid Search Configuration

```
RETRIEVAL PARAMETERS:

Qdrant Semantic Search:
     top_k = 20
     score_threshold = 0.50 (hapus hasil irrelevant)
     ef = 128 (HNSW search expansion factor)

PostgreSQL BM25 / Full-text:
     ts_rank_cd dengan normalization = 32 (penalize panjang)
     limit = 20

RRF Fusion:
     k = 60
     weights: equal (1.0 semantic, 1.0 keyword)

Diversity Filter:
     max_per_doc = 3 chunks dari dokumen yang sama

Reranker:
     top_k input = 30 (setelah RRF)
     top_n output = 5 (default), configurable per intent

QUALITY GATES:
     Minimum final_score: 0.30 (chunk di bawah ini tidak masuk context)
     Minimum chunk_count: 1 (jika 0 result, informasikan ke user)
     Maximum context_tokens: 4096 (potong jika melebihi)
```

### Search Failure Modes & Fallback

```
FAILURE MODE 1: Query terlalu spesifik, 0 result dari semantic search
→ FALLBACK: Keyword-only search dengan relaxed query (hapus stopwords)
→ FALLBACK 2: Suggest ke user untuk memperluas query

FAILURE MODE 2: OCR quality buruk → chunk_text berkualitas rendah
→ INDICATOR: ocr_confidence < 0.70 di semantic_metadata
→ WARNING: tampilkan indicator "Kualitas OCR rendah — hasil mungkin tidak akurat"

FAILURE MODE 3: LLM gagal generate (timeout, error)
→ FALLBACK: Tampilkan raw retrieved chunks tanpa LLM synthesis
→ User mendapat "Daftar dokumen relevan" tanpa narasi AI

FAILURE MODE 4: Qdrant tidak available
→ FALLBACK: PostgreSQL full-text search only
→ DEGRADED MODE: semantic ranking tidak tersedia, keyword only
→ Alert ops team
```

---

## RECOMMENDATION

### REC-01 — Prioritas Implementasi Pipeline
Urutan yang direkomendasikan berdasarkan dependencies:

```
1. PIPELINE-02 (OCR)         — fondasi semua pipeline lain
2. PIPELINE-01 (Ingestion)   — gerbang masuk dokumen
3. PIPELINE-03 (NER)         — metadata extraction
4. PIPELINE-05 (Chunking)    — RAG prerequisite
5. PIPELINE-06 (Embedding)   — RAG prerequisite
6. PIPELINE-07 (Retrieval)   — search capability
7. PIPELINE-08 (Reranking)   — search quality
8. PIPELINE-09 (Citation)    — response quality
9. PIPELINE-11 (AI Assist)   — full AI experience
10. PIPELINE-04 (Tagging)    — enhancement
11. PIPELINE-12 (Audit)      — compliance
12. PIPELINE-13 (Vector Sync)— maintenance
13. PIPELINE-15 (Re-indexing)— maintenance
```

### REC-02 — OCR Quality adalah Investasi Terpenting
Sebelum build retrieval, pastikan OCR pipeline menghasilkan output dengan
confidence ≥ 0.85 pada mayoritas dokumen notaris. Jika tidak, quality RAG
tidak bisa diselamatkan downstream. Investasi di OCR pre/post-processing
lebih berharga dari fine-tuning reranker.

### REC-03 — Redaksi Sensitif Harus Ditest Sebelum Embedding
Buat test suite yang memverifikasi bahwa NIK, nilai transaksi, dan data
sensitif lain TIDAK muncul dalam `chunk_text` yang masuk ke Qdrant payload.
Ini adalah security test yang wajib pass sebelum production deploy.

### REC-04 — REGULASI_PASAL adalah Feature Release-2
Hierarki pasal REGULASI adalah fitur yang powerful tapi kompleks. Rekomendasi:
- Release-1: REGULASI dichunk dengan Strategy-B (pasal-level)
- Release-2: Implement REGULASI_PASAL entity dengan full hierarchy + citation ke ayat

### REC-05 — Monitoring Wajib untuk Pipeline Health
Metrics yang perlu di-track sejak awal:
- OCR confidence distribution (histogram per minggu)
- Embedding latency (P50, P95, P99)
- RAG query latency (end-to-end)
- Retrieval score distribution (apakah scores degradasi?)
- Qdrant vs PostgreSQL sync discrepancy rate
- Reranking delta (seberapa besar reranker mengubah urutan?)

### REC-06 — Ambiguity: Indonesian NLP Model untuk NER
Saat ini belum ada konfirmasi model NER Bahasa Indonesia mana yang akan
digunakan. Opsi:
1. **IndoBERT NER** — pre-trained pada corpus Indonesia, tidak domain-spesifik
2. **Custom NER** — fine-tuned pada dokumen akta (butuh labeled dataset)
3. **Rule-based only** — regex + pattern matching (cepat tapi limited)
4. **LLM-based NER** — gunakan local LLM untuk ekstraksi (lambat tapi fleksibel)

**Rekomendasi:** Mulai dengan kombinasi rule-based (untuk field berstruktur)
+ IndoBERT (untuk nama pihak). Fine-tune NER setelah cukup labeled data.

---

## STATUS

```
STEP 1 — ANALYZE NEW DOMAIN         ✅ COMPLETE
STEP 2 — DDL DESIGN                 ✅ COMPLETE (pending final review)
STEP 3 — INGESTION & RETRIEVAL ARCH ✅ COMPLETE (pending approval)
STEP 4 — API DESIGN                 ⏸ WAITING APPROVAL
STEP 5 — FRONTEND SCREENS           ⏸ WAITING APPROVAL
STEP 6 — BACKEND IMPLEMENTATION     ⏸ WAITING APPROVAL
STEP 7 — RAG PIPELINE IMPLEMENTATION⏸ WAITING APPROVAL
```

**Menunggu konfirmasi 2 ambiguity sebelum lanjut ke STEP 4:**

1. **NER Model** → rule-based only? IndoBERT? LLM-based? kombinasi?
2. **REGULASI_PASAL** → masuk STEP 4 (prioritas tinggi) atau defer ke Release-2?

---

*Generated by: NOTARIST RAG PLATFORM — ANALYSIS_FIRST mode*
*File: /generated/docs/step3_ingestion_retrieval_architecture.md*
*Date: 2026-05-23*
