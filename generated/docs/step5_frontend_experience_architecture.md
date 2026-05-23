# STEP 5 — FRONTEND EXPERIENCE & SCREEN ARCHITECTURE
# NOTARIST RAG PLATFORM — REACT NATIVE

**Version:** v1.0
**Date:** 2026-05-23
**Mode:** ANALYSIS_FIRST
**Status:** DRAFT — Pending Approval
**Scope:** App architecture, screen hierarchy, UX flows, streaming, offline, accessibility

---

## SUMMARY

STEP 5 mendefinisikan arsitektur lengkap frontend React Native untuk NOTARIST RAG Platform.
Tidak ada implementasi code — murni UX architecture dan design blueprint.

**Confirmed Decisions yang berlaku di STEP 5:**

| Decision | Value |
|---|---|
| Platform | React Native (cross-platform: Android + iOS) |
| Object Storage | MinIO dengan 6 bucket (raw, ocr, processed, chunk, export, backup) |
| AI Response Mode | Streaming SSE token-by-token (default), non-streaming sebagai fallback |
| Citation Strategy | Citation-first — AI response tidak valid tanpa source reference |
| Upload Flow | Signed URL → direct upload ke MinIO notarist-raw |
| Download Flow | Signed URL → served dari MinIO notarist-processed |

**Design Philosophy:**
- **AI-first UX**: pencarian natural language adalah entry point utama
- **Citation-first**: setiap jawaban AI selalu menampilkan sumber
- **Legal-centric**: terminologi, workflow, dan visual mengikuti konteks notaris
- **Progressive disclosure**: kompleksitas tersembunyi, hanya ditampilkan saat dibutuhkan
- **Audit-visible**: user selalu sadar bahwa aktivitas mereka tercatat

---

## APP ARCHITECTURE

### Technology Stack

```
FRAMEWORK:        React Native (latest stable)
LANGUAGE:         TypeScript (strict mode)
STATE MANAGEMENT:
  Server state:   TanStack Query (React Query v5)
  Local state:    Zustand
  Form state:     React Hook Form
NAVIGATION:       React Navigation v6
NETWORKING:
  REST:           Axios with interceptors
  SSE Streaming:  EventSource polyfill (react-native-sse)
  File upload:    react-native-blob-util (signed URL upload)
STORAGE:
  Persistent:     AsyncStorage (non-sensitive), Expo SecureStore (tokens)
  Files (temp):   react-native-fs
ANIMATIONS:       React Native Reanimated v3
GESTURES:         React Native Gesture Handler
FILE PICKER:      react-native-document-picker
PDF VIEWER:       react-native-pdf
CONNECTIVITY:     @react-native-community/netinfo
NOTIFICATIONS:    react-native-push-notification (FCM + APNs)
GRAPH VIZ:        react-native-svg (custom force graph)
```

### Project Structure

```
src/
├── api/
│   ├── client.ts             Axios instance + interceptors (JWT inject, refresh)
│   ├── sseClient.ts          SSE client + reconnect strategy
│   ├── endpoints/
│   │   ├── auth.api.ts
│   │   ├── document.api.ts
│   │   ├── search.api.ts
│   │   ├── assistant.api.ts
│   │   ├── regulation.api.ts
│   │   ├── citation.api.ts
│   │   ├── audit.api.ts
│   │   └── admin.api.ts
│   └── storage/
│       └── minio.api.ts      Signed URL request + direct MinIO upload
│
├── components/
│   ├── common/               Button, Input, Badge, Card, Skeleton, Modal
│   ├── document/             DocumentCard, StatusBadge, ClassificationBadge
│   ├── search/               SearchBar, FilterPanel, ResultCard
│   ├── assistant/            ChatBubble, CitationCard, TypingIndicator, StreamToken
│   ├── regulation/           PasalItem, BabItem, RegulationTree
│   ├── graph/                RelationshipNode, RelationshipEdge, GraphCanvas
│   └── upload/               FilePicker, UploadProgress, PipelineStatus
│
├── hooks/
│   ├── useSearch.ts          hybrid search + filter management
│   ├── useAssistant.ts       SSE stream + conversation management
│   ├── useDocumentUpload.ts  signed URL + MinIO upload + pipeline polling
│   ├── useOffline.ts         connectivity + queue management
│   ├── usePermissions.ts     role-based UI permission check
│   └── useAuditTrail.ts      audit log query
│
├── navigation/
│   ├── RootNavigator.tsx     Auth gate
│   ├── AuthNavigator.tsx     Login stack
│   ├── MainNavigator.tsx     Bottom tab + modal stack
│   ├── DocumentNavigator.tsx Stack within Documents tab
│   └── AssistantNavigator.tsx Stack within Assistant tab
│
├── screens/                  (see SCREEN HIERARCHY section)
│
├── store/
│   ├── auth.store.ts         user, role, token, session
│   ├── ui.store.ts           theme, loading states, notifications
│   ├── offline.store.ts      queue, sync status
│   └── conversation.store.ts active conversation state
│
├── types/
│   ├── api.types.ts          API request/response types
│   ├── domain.types.ts       Document, Akta, Chunk, Citation, Regulation
│   └── navigation.types.ts   RootParamList, TabParamList, etc.
│
└── utils/
    ├── fieldMasker.ts        Client-side masking untuk field sensitif di display
    ├── legalAbbreviations.ts APHT, SKMHT, dll → full name
    ├── dateFormatter.ts      Tanggal Indonesia format
    └── tokenCounter.ts       Estimate token count untuk context preview
```

### MinIO Integration (Frontend View)

```
UPLOAD FLOW:
  1. User picks file via react-native-document-picker
  2. App calls: POST /api/v1/documents/upload/initiate
               {filename, mimeType, fileSize, jenisDokumen, klasifikasi}
  3. Backend returns: {uploadUrl (signed MinIO URL), docId, expiresIn}
  4. App uploads file DIRECTLY to MinIO (notarist-raw bucket):
               PUT {uploadUrl} with file binary
     → Bypasses backend, lebih efisien untuk file besar
  5. App calls: POST /api/v1/documents/upload/confirm {docId}
  6. Backend validates file landed in MinIO, starts pipeline

DOWNLOAD / VIEW FLOW:
  1. App calls: GET /api/v1/documents/{docId}/download-url
  2. Backend returns: {downloadUrl (signed URL), expiresIn: 3600}
     URL points to notarist-processed bucket (normalized PDF)
  3. App opens react-native-pdf with {downloadUrl}
  4. PDF rendered inline

EXPORT FLOW:
  1. App calls: POST /api/v1/assistant/export/{responseId}
  2. Backend generates citation package → uploads to notarist-export bucket
  3. Returns: {exportUrl (signed URL)}
  4. App downloads via react-native-blob-util → share or save
```

### Query Caching Strategy (React Query)

```
CACHE CONFIGURATION:
  Document list:       staleTime: 5min,  gcTime: 30min
  Document detail:     staleTime: 2min,  gcTime: 15min
  Document status:     staleTime: 10s,   gcTime: 1min   (polling during pipeline)
  Search results:      staleTime: 1min,  gcTime: 10min
  Regulation structure:staleTime: 24h,   gcTime: 48h    (changes rarely)
  Regulation pasal:    staleTime: 24h,   gcTime: 48h
  User profile:        staleTime: 10min, gcTime: 60min
  Conversation list:   staleTime: 30s,   gcTime: 5min
  Audit log:           staleTime: 30s,   gcTime: 5min   (real-time-ish)

PERSISTENT CACHE (AsyncStorage):
  recent_searches     last 20 queries (user-level)
  filter_preferences  last applied filters per screen
  conversation_cache  last 10 conversations (metadata only)
  regulation_tree     regulation structure snapshot (keyed by regulasiId)

SECURE STORAGE (Expo SecureStore):
  access_token        JWT access token
  refresh_token       JWT refresh token
  user_id
  user_role
```

---

## SCREEN HIERARCHY

### Navigation Tree

```
RootNavigator (Stack)
│
├── [unauthenticated]
│   └── AuthNavigator (Stack)
│         └── LoginScreen
│
└── [authenticated]
    ├── MainNavigator (Bottom Tab)
    │   ├── Tab: BERANDA
    │   │     └── DashboardScreen
    │   │
    │   ├── Tab: CARI
    │   │     ├── SearchScreen              (entry point, search input)
    │   │     └── SearchResultScreen        (results list)
    │   │
    │   ├── Tab: ASISTEN AI
    │   │     ├── ConversationListScreen    (history semua percakapan)
    │   │     └── AssistantChatScreen       (chat + streaming UI)
    │   │
    │   ├── Tab: DOKUMEN
    │   │     ├── DocumentListScreen        (daftar semua dokumen)
    │   │     ├── DocumentDetailScreen      (detail + tabs)
    │   │     ├── DocumentUploadScreen      (upload form + progress)
    │   │     ├── OcrReviewScreen           (manual review queue)
    │   │     ├── ChunkExplorerScreen       (chunk list + detail)
    │   │     └── RelationshipGraphScreen   (force-directed graph)
    │   │
    │   └── Tab: PROFIL
    │         ├── ProfileScreen             (current user info)
    │         ├── AuditScreen               [PIMPINAN, ADMIN]
    │         └── AdminScreen               [ADMIN only]
    │
    └── ModalStack (overlay semua tab)
          ├── RegulationExplorerModal       (full regulation tree + pasal)
          ├── CitationFullViewModal          (cited text + PDF page viewer)
          ├── DocumentViewerModal            (inline PDF viewer)
          ├── PasalDetailModal               (detail satu pasal + ayat)
          └── AuditDetailModal               (detail satu audit event)
```

---

### Screen Specifications

#### SCR-01: Login Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │                                      │
  │         [NOTARIST AI Logo]           │
  │    "Platform Dokumen Legal Notaris"  │
  │                                      │
  │  ┌────────────────────────────────┐  │
  │  │ Username                       │  │
  │  └────────────────────────────────┘  │
  │  ┌────────────────────────────────┐  │
  │  │ Password                   [👁]│  │
  │  └────────────────────────────────┘  │
  │                                      │
  │  ┌────────────────────────────────┐  │
  │  │         MASUK                  │  │
  │  └────────────────────────────────┘  │
  │                                      │
  │  v1.0.0 | Internal Use Only         │
  └──────────────────────────────────────┘

BEHAVIOR:
  Error states:
  - Credentials salah → inline error "Username atau password salah"
  - Akun suspended → "Akun Anda ditangguhkan. Hubungi administrator."
  - Server error → "Sistem tidak dapat dihubungi. Coba lagi."
  After login: navigate to DashboardScreen, store tokens in SecureStore
  No persistent login session — refresh token only (7 hari)
```

#### SCR-02: Dashboard Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ Selamat datang, [Nama]    [Role Badge]│
  │ [tanggal hari ini]                   │
  ├──────────────────────────────────────┤
  │ ┌──────────────────────────────────┐ │
  │ │ 🔍 Cari dokumen atau tanya AI... │ │
  │ └──────────────────────────────────┘ │
  ├──────────────────────────────────────┤
  │ STATISTIK SINGKAT                    │
  │ ┌────────────┐  ┌────────────────┐  │
  │ │ 1.247      │  │ 23             │  │
  │ │ Dok. Aktif │  │ Dok. Proses    │  │
  │ └────────────┘  └────────────────┘  │
  │ ┌────────────┐  ┌────────────────┐  │
  │ │ 3          │  │ 0.86           │  │
  │ │ Perlu Review│  │ Rata OCR Score │  │
  │ └────────────┘  └────────────────┘  │
  ├──────────────────────────────────────┤
  │ PENCARIAN TERAKHIR                   │
  │ ○ "APHT atas nama Budi Santoso"      │
  │ ○ "syarat roya hak tanggungan"       │
  │ ○ "akta jual beli bulan Maret"       │
  ├──────────────────────────────────────┤
  │ [ADMIN/PIMPINAN] REVIEW QUEUE        │
  │ ⚠ 3 dokumen memerlukan review manual │
  │ [Lihat Queue →]                      │
  └──────────────────────────────────────┘

BEHAVIOR:
  Quick search bar → navigates to SearchScreen with query pre-filled
  Statistics polling: setiap 5 menit (staleTime: 5min)
  Review queue alert: hanya tampil untuk NOTARIS, PIMPINAN, ADMIN
  Recent searches: dari AsyncStorage (lokal)
```

#### SCR-03: Search Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← [Search Bar — prominent]   [Filter]│
  ├──────────────────────────────────────┤
  │ Mode: [Hybrid ▼] [Semantik] [Keyword]│
  ├──────────────────────────────────────┤
  │ [Filter Panel — collapsed by default]│
  │  ▼ Jenis Dokumen                     │
  │  ▼ Jenis Akta                        │
  │  ▼ Rentang Tanggal                   │
  ├──────────────────────────────────────┤
  │ PENCARIAN TERBARU                    │
  │ [chip] APHT 2024  [chip] fidusia     │
  │ [chip] roya sertifikat HGB           │
  ├──────────────────────────────────────┤
  │ [Empty state illustration]           │
  │ "Ketik untuk mulai mencari           │
  │  dokumen hukum notaris"              │
  └──────────────────────────────────────┘

BEHAVIOR:
  Real-time debounce 500ms: show suggestions after 3 chars
  Suggestions: dari GET /search/suggest?q={input}
  Submit (Enter/Search button): navigate to SearchResultScreen
  Filter panel: persisted in filter_preferences (AsyncStorage)
  Mode switch: changes search behavior, visual indicator
```

#### SCR-04: Search Result Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← "APHT atas nama Budi"   [Filter 🔧]│
  │ 47 hasil · 0.8 detik · Hybrid        │
  ├──────────────────────────────────────┤
  │ Sort: [Relevansi ▼] [Tanggal] [Jenis]│
  ├──────────────────────────────────────┤
  │ ┌──────────────────────────────────┐ │
  │ │ [APHT] CONFIDENTIAL              │ │
  │ │ APHT Nomor 45/2024               │ │
  │ │ Tuan Budi Santoso — 12 Mar 2024  │ │
  │ │ "...pemberian hak tanggungan     │ │
  │ │  peringkat pertama atas..."      │ │
  │ │ [Relevansi ████████░░ 0.92]      │ │
  │ └──────────────────────────────────┘ │
  │ ┌──────────────────────────────────┐ │
  │ │ [AKTA] INTERNAL                  │ │
  │ │ AJB Nomor 12/2024                │ │
  │ │ ...                              │ │
  │ └──────────────────────────────────┘ │
  │ ...more results...                   │
  ├──────────────────────────────────────┤
  │ ┌──────────────────────────────────┐ │
  │ │ 🤖 Tanya AI tentang hasil ini   │ │
  │ └──────────────────────────────────┘ │
  └──────────────────────────────────────┘

BEHAVIOR:
  Relevance bar: visual score indicator (0.0-1.0)
  Classification badge: color-coded (PUBLIC=hijau, INTERNAL=biru, CONFIDENTIAL=oranye)
  Tap card: if AKTA → DocumentDetailScreen; if SOP/REGULASI → appropriate screen
  "Tanya AI": navigates to AssistantChatScreen dengan pre-set filter dari hasil search
  Load more: infinite scroll (React Query infinite query)
  Field masking: snippet tidak boleh tampilkan NIK atau nilai transaksi
```

#### SCR-05: AI Assistant Chat Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← Percakapan Hukum    [Filter] [···] │
  │ APHT · CONFIDENTIAL                  │
  ├──────────────────────────────────────┤
  │                                      │
  │         [User bubble]                │
  │  "Apa syarat pemberian APHT?"    ▶  │
  │                           12:31 WIB  │
  │                                      │
  │  ┌────────────────────────────────┐  │
  │  │ 🤖 AI NOTARIST               │  │
  │  │                                │  │
  │  │ Berdasarkan UUHT dan APHT      │  │  ← Streaming: text muncul bertahap
  │  │ No. 45/2024 [1], syarat        │  │
  │  │ pemberian APHT meliputi:       │  │
  │  │                                │  │
  │  │ 1. Adanya perjanjian kredit... │  │
  │  │ 2. Obyek hak tanggungan...     │  │
  │  │                                │  │
  │  │ [HIGH] ●●●○○                  │  │  ← Confidence indicator
  │  │                                │  │
  │  │ SUMBER                         │  │
  │  │ ┌──────────────────────────┐   │  │
  │  │ │ [1] APHT No. 45/2024     │   │  │  ← Citation card
  │  │ │ Halaman 3 · CONFIDENTIAL  │   │  │
  │  │ │ "...syarat pemberian..."  │   │  │
  │  │ │ [Lihat Dokumen →]         │   │  │
  │  │ └──────────────────────────┘   │  │
  │  │ ┌──────────────────────────┐   │  │
  │  │ │ [2] UU No. 4/1996 Psl 8  │   │  │
  │  │ │ Pasal 8 Ayat (1)         │   │  │
  │  │ │ "Pemberi Hak Tanggungan  │   │  │
  │  │ │  adalah pihak yang..."    │   │  │
  │  │ │ [Lihat Regulasi →]        │   │  │
  │  │ └──────────────────────────┘   │  │
  │  │                                │  │
  │  │ SARAN LANJUTAN                 │  │
  │  │ [Apa itu SKMHT?]              │  │
  │  │ [Bagaimana proses roya?]       │  │
  │  └────────────────────────────────┘  │
  │                         12:31 WIB    │
  │                                      │
  ├──────────────────────────────────────┤
  │ [Filter: APHT ×] [+ Filter]         │
  │ ┌────────────────────────────┐ [→]  │
  │ │ Ketik pertanyaan hukum...   │      │
  │ └────────────────────────────┘      │
  └──────────────────────────────────────┘

STREAMING BEHAVIOR:
  Phase 1: Typing indicator (animated dots) → saat LLM mulai generate
  Phase 2: Token-by-token: text muncul char-by-char atau word-by-word
  Phase 3: "sources" SSE event → citation cards muncul di bawah bubble
  Phase 4: "done" SSE event → saran lanjutan muncul
  Stop button: muncul selama streaming, menghentikan SSE + LLM generation

CITATION CARD BEHAVIOR:
  Tap citation card → CitationFullViewModal
  Citation [1] di teks → terhubung ke citation card (visual highlight)
  [Lihat Regulasi] → RegulationExplorerModal fokus ke pasal yang dikutip

FILTER DISPLAY:
  Active filters tampil sebagai chips di atas input bar
  Tap [+ Filter] → FilterPanel overlay dari bawah layar
```

#### SCR-06: Citation Full View Modal

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ✕                    SUMBER [1]      │
  ├──────────────────────────────────────┤
  │ APHT Nomor 45/2024                   │
  │ [CONFIDENTIAL] [APHT] 12 Mar 2024    │
  │ Notaris: Antonius, S.H.              │
  ├──────────────────────────────────────┤
  │ TEKS YANG DIKUTIP (Halaman 3)        │
  │ ┌──────────────────────────────────┐ │
  │ │ "...pemberian hak tanggungan     │ │
  │ │ [peringkat pertama] atas bidang  │ │  ← quoted text highlighted
  │ │ tanah Sertifikat HGB No. 123..."  │ │
  │ └──────────────────────────────────┘ │
  │ Relevansi: ████████░░ 0.92           │
  ├──────────────────────────────────────┤
  │ KONTEKS (Teks sekitar)               │
  │ [Chunk N-1 snippet]                  │
  │ ▶ [CHUNK SUMBER ← highlighted]       │
  │ [Chunk N+1 snippet]                  │
  ├──────────────────────────────────────┤
  │ [Lihat Dokumen Lengkap]              │
  │ [Unduh Dokumen] (if permitted)       │
  └──────────────────────────────────────┘

UNTUK REGULASI CITATION:
  Header: UU No. 4 Tahun 1996 — UUHT
  "Pasal 8 Ayat (1)" (instead of "Halaman 3")
  [Lihat Regulasi →] button → RegulationExplorerModal
```

#### SCR-07: Document List Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ DOKUMEN               [Filter] [🔍]  │
  ├──────────────────────────────────────┤
  │ Semua │ Akta │ SOP │ Regulasi │ Lain │  ← horizontal filter tabs
  ├──────────────────────────────────────┤
  │ ┌──────────────────────────────────┐ │
  │ │ [APHT] ● AKTIF                   │ │
  │ │ APHT Nomor 45/2024               │ │
  │ │ Tuan Budi — Ant., S.H.           │ │
  │ │ Upload: 12 Mar 2024              │ │
  │ │ OCR: ████████░░ 0.87             │ │
  │ └──────────────────────────────────┘ │
  │ ┌──────────────────────────────────┐ │
  │ │ [SOP] ● AKTIF                    │ │
  │ │ SOP Pelayanan APHT               │ │
  │ │ SOP-NOT-001 · v2                 │ │
  │ │ Upload: 01 Jan 2024              │ │
  │ └──────────────────────────────────┘ │
  │ ┌──────────────────────────────────┐ │
  │ │ [REGULASI] ⏳ PROSES OCR         │ │
  │ │ UU No. 4 Tahun 1996              │ │
  │ │ Upload: 23 Mei 2026              │ │
  │ │ [Pipeline: OCR ████░░░░░░]       │ │
  │ └──────────────────────────────────┘ │
  │ ...                                  │
  └──────────────────────────────────────┘
  [+] FAB (Floating Action Button) — upload dokumen baru

STATUS BADGES:
  ● AKTIF         → hijau, dapat dicari
  ⏳ PROSES       → biru, pipeline berjalan
  ⚠ REVIEW       → kuning, perlu manual review
  ✕ GAGAL        → merah, pipeline error
  🗃 ARSIP       → abu-abu, diarsipkan

LONG PRESS CONTEXT MENU:
  Lihat Detail
  Unduh (jika permitted)
  Lihat Relasi Dokumen
  Arsipkan (NOTARIS+)
  Hapus (PIMPINAN, ADMIN)
```

#### SCR-08: Document Detail Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← APHT No. 45/2024   [···] [Download]│
  │ [CONFIDENTIAL]  [APHT]  ● AKTIF      │
  ├──────────────────────────────────────┤
  │ INFO │ DOKUMEN │ CHUNKS │ RELASI │ LOG│  ← tab bar
  ├──────────────────────────────────────┤
  │ TAB: INFO                            │
  │                                      │
  │ Nomor Akta:   45/2024                │
  │ Jenis:        APHT                   │
  │ Tanggal:      12 Maret 2024          │
  │ Notaris:      Antonius, S.H.         │
  │ PPAT:         —                      │
  │                                      │
  │ PARA PIHAK                           │
  │ Pemberi HT:   Budi Santoso           │
  │               ****-****-0234 (NIK)   │  ← masked
  │ Penerima HT:  PT. Bank Maju          │
  │                                      │
  │ SERTIFIKAT TERKAIT                   │
  │ SHM No. 123/Kel.Sudirman             │
  │ Luas: 250 m²                         │
  │                                      │
  │ Nilai Jaminan: *** (CONFIDENTIAL)    │  ← masked untuk STAFF
  │                                      │
  │ [🤖 Tanya AI tentang dokumen ini →] │
  └──────────────────────────────────────┘

TAB: DOKUMEN → PDF viewer (inline, dari MinIO signed URL)
TAB: CHUNKS → ChunkExplorerScreen embedded
TAB: RELASI → RelationshipGraphScreen embedded (local graph centered on this doc)
TAB: LOG → Document-specific audit trail (PIMPINAN, ADMIN)
```

#### SCR-09: Document Upload Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← UPLOAD DOKUMEN                     │
  ├──────────────────────────────────────┤
  │                                      │
  │  ┌────────────────────────────────┐  │
  │  │                                │  │
  │  │    [📄 Pilih File]            │  │
  │  │    PDF, TIFF, JPG, PNG, DOCX  │  │
  │  │    Maks. 100 MB               │  │
  │  │                                │  │
  │  └────────────────────────────────┘  │
  │                                      │
  │  [File terpilih: akta_apht_45.pdf]  │
  │  [Preview thumbnail atau PDF icon]   │
  │                                      │
  ├──────────────────────────────────────┤
  │  Jenis Dokumen *                     │
  │  [AKTA ▼]                           │
  │                                      │
  │  Klasifikasi *                       │
  │  [CONFIDENTIAL ▼]                   │
  │  ⚠ Dokumen ini akan dibatasi        │
  │    aksesnya untuk STAFF             │
  │                                      │
  │  Keterangan (opsional)               │
  │  ┌────────────────────────────────┐  │
  │  │                                │  │
  │  └────────────────────────────────┘  │
  ├──────────────────────────────────────┤
  │                                      │
  │  ┌────────────────────────────────┐  │
  │  │         UPLOAD DOKUMEN         │  │
  │  └────────────────────────────────┘  │
  └──────────────────────────────────────┘

UPLOAD PROGRESS STATES:
  State 1: Validasi file (type, size, malware pre-check)
  State 2: Mengunggah ke penyimpanan (upload bar %)
  State 3: Memproses OCR... (with estimasi waktu)
  State 4: Pipeline progress (via polling GET /documents/{id}/status)
           "OCR ✓ · NER ⏳ · Embedding ⏸"
  State 5: Selesai — "Dokumen aktif dan dapat dicari"

WARNING UNTUK KLASIFIKASI TINGGI:
  Jika user memilih STRICTLY_CONFIDENTIAL → bottom sheet confirmation:
  "Dokumen ini hanya dapat diakses oleh Pimpinan dan Administrator.
   Pastikan klasifikasi ini benar sebelum melanjutkan."
  [Batal] [Konfirmasi]
```

#### SCR-10: OCR Review Screen (Manual Review Queue)

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← REVIEW DOKUMEN                     │
  │ OCR Confidence: 0.58 ⚠               │
  ├──────────────────────────────────────┤
  │ HASIL SCAN ASLI                      │
  │ ┌──────────────────────────────────┐ │
  │ │  [Image halaman 1]               │ │
  │ │  (pan & zoom enabled)            │ │
  │ └──────────────────────────────────┘ │
  │ Halaman [1] / 5 : ← →               │
  ├──────────────────────────────────────┤
  │ HASIL EKSTRAKSI TEKS                 │
  │ ┌──────────────────────────────────┐ │
  │ │ Nomor Akta: 45/20Z4       ⚠ 0.51│ │  ← merah, confidence rendah
  │ │ [Edit]                           │ │
  │ │ Tanggal:  12 Maret 2024   ✓ 0.92│ │  ← hijau, confidence tinggi
  │ │ Notaris:  Ant0nius, S.H   ⚠ 0.61│ │  ← kuning, perlu cek
  │ │ [Edit]                           │ │
  │ │ Client:   Budi Sant0so    ⚠ 0.63│ │
  │ │ [Edit]                           │ │
  │ └──────────────────────────────────┘ │
  ├──────────────────────────────────────┤
  │ [✕ TOLAK — Minta Scan Ulang]        │
  │ [✓ SETUJUI — Lanjutkan Pipeline]    │
  └──────────────────────────────────────┘

UX DETAIL:
  Confidence coloring:
    ≥ 0.85 → hijau (✓)
    0.70-0.84 → kuning (⚠)
    < 0.70 → merah (⚠⚠)
  Tap [Edit] → inline text field untuk koreksi manual
  OCR confidence bar per halaman di bagian header
  Sticky bottom bar untuk action buttons
```

#### SCR-11: Chunk Explorer Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← CHUNK EXPLORER                     │
  │ APHT No. 45/2024 · 24 chunks         │
  ├──────────────────────────────────────┤
  │ Filter: Semua │ CLAUSE │ ARTICLE│TABLE│
  ├──────────────────────────────────────┤
  │ ┌──────────────────────────────────┐ │
  │ │ Chunk #1 · CLAUSE · Hal. 1      │ │
  │ │ 412 token · ✓ INDEXED           │ │
  │ │ "Nomor: 45/2024 — Pada hari ini │ │
  │ │  Selasa tanggal dua belas..."   │ │
  │ │ [Cari Serupa ▷]                 │ │
  │ └──────────────────────────────────┘ │
  │ ┌──────────────────────────────────┐ │
  │ │ Chunk #2 · CLAUSE · Hal. 1-2    │ │
  │ │ 538 token · ✓ INDEXED           │ │
  │ │ "---KOMPARISI--- Tuan [NAME]    │ │
  │ │  lahir di [REDACTED]..."        │ │  ← REDACTED displayed
  │ └──────────────────────────────────┘ │
  │ ...                                  │
  └──────────────────────────────────────┘

CHUNK DETAIL (expandable):
  Tap chunk → expand view:
  - Full chunk_text (redacted version)
  - Embedding status badge
  - OCR confidence dari page source
  - Qdrant point ID (admin only)
  - [Cari Dokumen Serupa] → SearchResultScreen dengan semantic search dari chunk ini

REDACTED DISPLAY:
  [REDACTED_NIK] → tampil sebagai chip berwarna abu-abu
  [REDACTED_AMOUNT] → sama
  Tooltip saat tap chip: "Data sensitif disembunyikan sesuai kebijakan privasi"
```

#### SCR-12: Relationship Graph Screen

```
LAYOUT:
  ┌──────────────────────────────────────┐
  │ ← RELASI DOKUMEN        [Filter] [⊞] │
  ├──────────────────────────────────────┤
  │                                      │
  │     [SKMHT]                          │
  │        │ PRECEDES                    │
  │        ▼                             │
  │     [APHT 45/2024] ← CENTER NODE    │
  │        │                 │           │
  │  CANCELS                REF          │
  │        ▼                 ▼           │
  │     [ROYA]          [SERTIFIKAT]     │
  │                                      │
  │  (force-directed graph, pan & zoom)  │
  │                                      │
  ├──────────────────────────────────────┤
  │ LEGENDA                              │
  │ ●AKTA  ●SOP  ●REGULASI  ●SERTIFIKAT│
  │ ─PRECEDES  ─CANCELS  ─REFERENCED   │
  ├──────────────────────────────────────┤
  │ Filter Relasi:                       │
  │ [☑ PRECEDES] [☑ CANCELS] [☑ REF]   │
  └──────────────────────────────────────┘

NODE INTERACTION:
  Tap node → popup mini-card:
    {docTitle, jenis, tanggal, status}
    [Lihat Dokumen] button
  Double-tap node → re-center graph on that node + load its relationships
  Long press edge → show relation details (type, created by, date)

VISUAL ENCODING:
  Node color: by jenis_dokumen
  Node size: larger = more relationships
  Edge style: solid = active relation, dashed = historical
  Edge label: relation type abbreviation
```

#### SCR-13: Regulation Explorer Modal

```
LAYOUT (Bottom Sheet Modal, full height):
  ┌──────────────────────────────────────┐
  │ ✕  UU No. 4 Tahun 1996 — UUHT       │
  │     Status: BERLAKU                  │
  │     [Riwayat Perubahan]              │
  ├──────────────────────────────────────┤
  │ 🔍 Cari dalam regulasi ini...        │
  ├──────────────────────────────────────┤
  │ STRUKTUR                             │
  │ ▼ BAB I — KETENTUAN UMUM            │
  │   ▼ Pasal 1                          │
  │     ○ Ayat (1): "Hak Tanggungan..."  │  ← tap → PasalDetailModal
  │     ○ Ayat (2): "Yang dimaksud..."   │
  │   ▷ Pasal 2                          │
  │   ▷ Pasal 3                          │
  │ ▶ BAB II — OBJEK HAK TANGGUNGAN     │
  │ ▶ BAB III — PEMBEBANAN...            │
  │ ▶ BAB IV — ...                       │
  ├──────────────────────────────────────┤
  │ [🤖 Tanya AI tentang UU ini →]      │
  └──────────────────────────────────────┘

CROSS-REFERENCE INDICATOR:
  Pasal yang dikutip oleh AI → badge kecil "AI 3×"
  Pasal yang berubah → badge "⚠ Diubah oleh PP No.../Tahun..."
  Tap badge → show amendment detail
```

#### SCR-14: Audit Screen

```
LAYOUT (PIMPINAN, ADMIN only):
  ┌──────────────────────────────────────┐
  │ ← AUDIT LOG                          │
  ├──────────────────────────────────────┤
  │ DOKUMEN │ PENGGUNA │ AI SESI │ SENSITIF│
  ├──────────────────────────────────────┤
  │ [Filter: Tanggal 1-31 Mei 2026]      │
  ├──────────────────────────────────────┤
  │ TAB: AI SESI                         │
  │ ┌──────────────────────────────────┐ │
  │ │ Annisa Indah · 23 Mei 12:31     │ │
  │ │ "Apa syarat pemberian APHT?"    │ │
  │ │ Akses: APHT/45/2024 + UUHT     │ │
  │ │ Risk: [LOW]                     │ │
  │ └──────────────────────────────────┘ │
  │ ┌──────────────────────────────────┐ │
  │ │ Budi Admin · 23 Mei 09:15       │ │
  │ │ "Tampilkan NIK Tuan Santoso"    │ │
  │ │ Akses: CLIENT/123               │ │
  │ │ Risk: [HIGH] ⚠                  │ │
  │ └──────────────────────────────────┘ │
  └──────────────────────────────────────┘

RISK BADGE COLORS:
  LOW → hijau
  MEDIUM → kuning
  HIGH → oranye
  CRITICAL → merah + notifikasi admin

AUDIT VISIBILITY PRINCIPLE:
  User STAFF tidak bisa lihat audit screen
  User bisa lihat aktivitas sendiri via "Riwayat Saya" di Profile
  Audit tidak bisa diedit atau dihapus dari UI
```

#### SCR-15: Admin Screen

```
LAYOUT (ADMIN only):
  ┌──────────────────────────────────────┐
  │ ← ADMIN PANEL                        │
  ├──────────────────────────────────────┤
  │ KESEHATAN PIPELINE                   │
  │ ┌─────────────────────────────────┐  │
  │ │ OCR Queue:      12 menunggu     │  │
  │ │ NER Queue:       5 menunggu     │  │
  │ │ Embedding:      24 menunggu     │  │
  │ │ Review Queue:    3 perlu review │  │
  │ │ Error 24 jam:    2 dokumen      │  │
  │ └─────────────────────────────────┘  │
  ├──────────────────────────────────────┤
  │ STATUS LAYANAN                       │
  │ OCR Service   ● Online               │
  │ NER Service   ● Online               │
  │ LLM Service   ● Online               │
  │ Qdrant DB     ● Online               │
  │ MinIO Storage ● Online               │
  ├──────────────────────────────────────┤
  │ QDRANT SYNC                          │
  │ 98.9% konsisten (1.234/1.247 chunk)  │
  │ [Cek Sekarang] [Perbaiki]            │
  ├──────────────────────────────────────┤
  │ AKSI CEPAT                           │
  │ [Re-index Dokumen]                   │
  │ [Lihat Review Queue]                 │
  │ [Ekspor Log Audit]                   │
  └──────────────────────────────────────┘
```

---

## USER FLOW

### UF-01: Upload → Active Document

```
[User tap FAB pada DocumentListScreen]
     ↓
[DocumentUploadScreen]
  Pilih file → preview tampil
  Set jenis + klasifikasi → form validated
  Tap [UPLOAD]
     ↓
[Upload State Machine]:
  1. "Memvalidasi file..." (malware check)
  2. "Mengunggah... 45%" (progress bar, MinIO direct upload)
  3. "Berhasil diunggah. Memulai proses OCR..."
  4. Navigate ke DocumentDetailScreen (status: PENDING_OCR)
     + polling setiap 10 detik via React Query refetchInterval
     ↓
[DocumentDetailScreen — pipeline progress visible]
  OCR ████░░░░░░ IN_PROGRESS
     ↓ (setelah OCR selesai)
  OCR ██████████ ✓  NER ████░░░░░░ IN_PROGRESS
     ↓ (setelah NER)
  OCR ✓  NER ✓  Embedding ██░░░░░░░░ IN_PROGRESS
     ↓ (setelah semua)
  Status: ● AKTIF — "Dokumen sudah dapat dicari"
  + Push notification (background): "APHT 45/2024 siap dicari"
```

### UF-02: Semantic Search → AI Ask

```
[User pada SearchScreen]
  Ketik: "syarat pemberian APHT"
  Tap [Cari] atau Enter
     ↓
[SearchResultScreen]
  Tampil hasil: 12 dokumen relevan
  User baca snippet, tertarik dengan konteks hukum
  User tap [🤖 Tanya AI tentang hasil ini]
     ↓
[AssistantChatScreen]
  Pre-filled: filter "APHT" aktif
  Pre-filled query suggestion: "Apa syarat pemberian APHT?"
  User edit atau langsung kirim
     ↓
[Streaming response muncul]
  Typing indicator → tokens → citation cards
     ↓
[User tap citation card]
     ↓
[CitationFullViewModal]
  Lihat teks yang dikutip + konteks sekitar
  User tap [Lihat Regulasi]
     ↓
[RegulationExplorerModal]
  Fokus ke Pasal 8 UUHT (yang dikutip AI)
  User baca pasal lengkap
```

### UF-03: OCR Manual Review Flow

```
[Dashboard — Review Queue Alert: "3 dokumen perlu review"]
  User tap [Lihat Queue]
     ↓
[DocumentListScreen — filter: MANUAL_REVIEW]
  User tap dokumen dengan badge ⚠ REVIEW
     ↓
[OcrReviewScreen]
  User lihat scan asli (atas) + hasil OCR (bawah)
  Terlihat: "Nomor Akta: 45/20Z4" (salah OCR, Z bukan 2)
  User tap [Edit] pada field Nomor Akta
  User koreksi: "45/2024"
  User tap [✓ SETUJUI — Lanjutkan Pipeline]
     ↓
[Konfirmasi bottom sheet]
  "Koreksi ini akan disimpan dan pipeline akan dilanjutkan. Setuju?"
  [Batal] [Ya, Lanjutkan]
     ↓
[Pipeline resumed]
  Navigate kembali ke DocumentListScreen
  Status dokumen berubah: PENDING_EXTRACTION → ... → ACTIVE
```

### UF-04: Legal Citation Trace

```
[AssistantChatScreen — response telah diterima]
  "Berdasarkan APHT No. 45/2024 [1], syarat APHT adalah..."
  User ingin tahu PERSIS bagian mana dari akta yang dijadikan dasar
     ↓
[User tap citation card [1]]
     ↓
[CitationFullViewModal]
  Tampil: "Diambil dari Halaman 3, Chunk #4"
  Tampil teks asli dari chunk (redacted version)
  User tap [Lihat Dokumen Lengkap]
     ↓
[DocumentViewerModal — PDF viewer]
  PDF dibuka, scroll ke halaman 3
  Chunk yang dikutip: highlighted dengan warna kuning
  User dapat membandingkan PDF asli dengan jawaban AI
```

---

## STREAMING FLOW

### SF-01: SSE Connection Lifecycle

```
[User tap SEND]
     │
[1]  Create SSE connection:
     EventSource(
       url: '/api/v1/assistant/ask/stream',
       headers: {Authorization: 'Bearer {token}'},
       method: 'POST',
       body: {queryText, conversationId, filters, topK}
     )
     │
[2]  Show TypingIndicator component (animated 3 dots)
     │
[3]  On event: "token" → append token to ChatBubble content
     Render: incremental text via Reanimated entering animation
     │
[4]  On event: "sources" → parse citation JSON
     Render: CitationCard components below ChatBubble (slide up animation)
     │
[5]  On event: "done" → parse {responseId, executionTimeMs}
     ├── Hide TypingIndicator
     ├── Show FollowUpChips (saran lanjutan)
     ├── Store conversation in AsyncStorage
     └── Close SSE connection
     │
[6]  On event: "error" → parse {errorCode, message}
     ├── Hide TypingIndicator
     ├── Show ErrorBubble with retry option
     └── Close SSE connection

STOP BUTTON BEHAVIOR:
     User tap [■ Stop]
     → Close SSE connection (EventSource.close())
     → POST /assistant/conversations/{id}/cancel (optional server-side cancel)
     → Show "Generasi dihentikan" indicator
     → Display partial response yang sudah muncul
     → Show [Lanjutkan?] atau [Kirim Ulang]
```

### SF-02: Incremental Citation Rendering

```
SSE "sources" event arrives:
  data: [{
    order: 1,
    docId: "...",
    nomorAkta: "45/2024",
    jenisDokumen: "AKTA",
    jenisAkta: "APHT",
    pageNumber: 3,
    snippet: "...pemberian hak tanggungan...",
    relevanceScore: 0.92,
    pasalRef: null
  }, {
    order: 2,
    docId: "...",
    nomorAkta: null,
    jenisDokumen: "REGULASI",
    jenisAkta: null,
    pageNumber: null,
    snippet: "...",
    relevanceScore: 0.88,
    pasalRef: {pasalId: "...", nomor: "8", ayatNomor: "(1)", regulasiJudul: "UUHT"}
  }]

RENDER SEQUENCE:
  1. Parse array
  2. Sort by order
  3. For each citation:
     a. If jenisDokumen = REGULASI + pasalRef → render RegulasiCitationCard
        Shows: "UU No.4/1996 · Pasal 8 Ayat (1)"
        Action: [Lihat Regulasi] → RegulationExplorerModal fokus ke pasal ini
     b. Else → render StandardCitationCard
        Shows: "APHT No. 45/2024 · Hal. 3"
        Action: [Lihat Dokumen] → CitationFullViewModal

TEXT-CITATION LINKING:
  In ChatBubble teks: "[1]" adalah tapable span
  Tap "[1]" → scroll to CitationCard [1] (jika below viewport)
           → flash CitationCard briefly (visual highlight)
```

### SF-03: Reconnect Strategy

```
SSE disconnects unexpectedly (network lost, timeout):

  Attempt 1: reconnect setelah 2 detik
  Attempt 2: reconnect setelah 4 detik
  Attempt 3: reconnect setelah 8 detik
  After 3 attempts: show "Koneksi terputus. Coba lagi?"
                    + [Kirim Ulang] button

OFFLINE DETECTION:
  NetInfo.addEventListener → detect offline
  If offline during streaming:
  → Stop SSE gracefully
  → Show banner: "Tidak ada koneksi internet"
  → Preserve partial response yang sudah tampil
  → Queue re-request for when online

RESPONSE-ID CONTINUITY:
  Jika reconnect berhasil sebelum LLM selesai:
  → Backend mendeteksi responseId yang sudah ada
  → Lanjutkan streaming dari posisi terakhir (jika LLM masih running)
  → Jika LLM sudah selesai → return full response non-streaming sebagai catch-up
```

---

## OFFLINE FLOW

### Offline Strategy Summary

Sistem ini adalah **server-dependent platform** — AI retrieval tidak dapat
bekerja offline. Offline strategy bersifat **defensive** (preserve UX saat
network hilang) bukan **full offline-capable**.

```
ONLINE CAPABILITIES:
  ✓ Full AI assistant (streaming)
  ✓ Semantic search
  ✓ Document upload
  ✓ Pipeline monitoring
  ✓ Regulation explorer
  ✓ Audit log

OFFLINE CAPABILITIES (cached data):
  ✓ Read recent search results (React Query cache, 30 min)
  ✓ Read document list metadata (React Query cache, 30 min)
  ✓ Read conversation history (AsyncStorage cache)
  ✓ Read regulation structure (AsyncStorage, 48 jam TTL)
  ✗ New search
  ✗ New AI query
  ✗ Upload document
  ✗ Real-time pipeline status

OFFLINE INDICATOR:
  Persistent banner di top: "Anda sedang offline — Fitur terbatas"
  Color: abu-abu
  Tap banner → show detail apa yang tersedia/tidak

UPLOAD QUEUE (offline):
  Jika user mencoba upload saat offline:
  → Simpan file metadata ke OfflineQueue (Zustand + AsyncStorage)
  → Show: "Upload akan dilanjutkan saat koneksi kembali"
  → Saat online: auto-resume upload
  → Max queue: 5 files (storage concern)
```

### Sync Strategy

```
SYNC ON RECONNECT (NetInfo.isConnected = true):

  1. Flush OfflineQueue:
     → Process queued uploads (satu per satu)
     → Update UI per item selesai

  2. Invalidate stale cache:
     → queryClient.invalidateQueries(['documents'])
     → queryClient.invalidateQueries(['search'])
     → Document status yang sedang PROSES → refetch

  3. Pull notifications missed:
     → GET /users/me/notifications?since={lastSync}

  4. Update regulation cache jika TTL expired:
     → Background refetch regulation structure

BACKGROUND SYNC:
  App di background: React Native AppState
  Saat foreground kembali (AppState = 'active'):
  → Invalidate document status queries (pipeline progress)
  → Invalidate notification badge count
```

---

## NOTIFICATION ARCHITECTURE

### Push Notification Types

```
NOTIFICATION CATEGORY: INGESTION
  Trigger: DocActiveEvent di backend
  Template: "Dokumen '{title}' sudah aktif dan dapat dicari"
  Action: deep link → DocumentDetailScreen({docId})
  Priority: NORMAL

NOTIFICATION CATEGORY: REVIEW_REQUIRED
  Trigger: Low OCR confidence → manual review queue
  Template: "Dokumen '{title}' memerlukan review manual (OCR: {score})"
  Action: deep link → OcrReviewScreen({docId})
  Target: NOTARIS, PIMPINAN, ADMIN roles
  Priority: HIGH

NOTIFICATION CATEGORY: PIPELINE_ERROR
  Trigger: PipelineFailureEvent setelah max retries
  Template: "Proses dokumen '{title}' gagal. Hubungi administrator."
  Action: deep link → DocumentDetailScreen({docId})
  Target: Uploader + ADMIN
  Priority: HIGH

NOTIFICATION CATEGORY: HIGH_RISK_AUDIT
  Trigger: ai_interaction_audit risk_level = HIGH atau CRITICAL
  Template: "⚠ Aktivitas berisiko tinggi terdeteksi"
  Action: deep link → AuditScreen (AI SESI tab)
  Target: ADMIN only
  Priority: URGENT

IN-APP NOTIFICATION:
  Bell icon di header → NotificationListScreen
  Badge count pada bell icon
  Tapped notification → mark as read → navigate
```

### Deep Link Structure

```
notarist-ai://document/{docId}          → DocumentDetailScreen
notarist-ai://document/{docId}/review   → OcrReviewScreen
notarist-ai://audit/session/{sessionId} → AuditDetailModal
notarist-ai://conversation/{convId}     → AssistantChatScreen
notarist-ai://regulation/{regulasiId}   → RegulationExplorerModal
```

---

## SECURITY UX

### Authentication UX

```
TOKEN EXPIRY HANDLING:
  Access token expires (1 jam) → auto-refresh via Axios interceptor
  If refresh fails (refresh token expired, revoked):
  → Clear all stored tokens
  → Show: AlertDialog "Sesi Anda telah berakhir. Silakan masuk kembali."
  → Navigate to LoginScreen

CONCURRENT SESSION HANDLING:
  Jika user login dari device lain → old session di-invalidate
  → Current device mendapat 401 pada next request
  → Auto-navigate ke LoginScreen dengan message:
    "Anda telah keluar karena login dari perangkat lain."

BIOMETRIC AUTH (future consideration):
  Opsional untuk re-auth saat app kembali dari background
  Tidak menggantikan server-side auth — hanya layer convenience
```

### Sensitive Data Display

```
MASKING RULES (per role):

  NIK:
    STAFF:          ****-****-****-**** (fully masked)
    NOTARIS+:       Dapat melihat clear dengan audit log

  NILAI_TRANSAKSI:
    STAFF:          "Tersembunyi (Confidential)"
    NOTARIS+:       Nilai ditampilkan + audit log triggered

  ALAMAT LENGKAP:
    STAFF:          Kota dan Provinsi saja
    NOTARIS+:       Alamat lengkap

  MASKING INDICATOR:
    Masked field ditampilkan sebagai chip abu-abu
    Tap chip (untuk NOTARIS+) → AlertDialog konfirmasi:
      "Melihat data sensitif ini akan tercatat dalam log audit.
       Lanjutkan?"
      [Batal] [Tampilkan]
    Jika lanjut → POST /audit/sensitive-access/acknowledge
              → Show clear value
              → Server logs access

CLASSIFICATION BADGE:
  Setiap dokumen yang ditampilkan wajib ada classification badge
  Tidak ada dokumen yang ditampilkan tanpa label klasifikasi
  User tahu selalu dokumen apa yang mereka baca
```

### Audit Awareness UX

```
AUDIT AWARENESS PRINCIPLE:
  User harus sadar bahwa aktivitas mereka tercatat.

IMPLEMENTATION:
  1. Footer kecil di AssistantChatScreen:
     "Semua percakapan tercatat untuk kepatuhan"

  2. Toast saat download dokumen:
     "Unduhan dokumen ini tercatat dalam log audit"

  3. Profile screen → "Riwayat Aktivitas Saya" (last 30 hari)
     User dapat melihat apa yang sudah mereka akses/tanya

  4. Clear masking flow: explicit confirmation + audit record
     (lihat SENSITIVE DATA DISPLAY di atas)
```

---

## ACCESSIBILITY STRATEGY

```
TOUCH TARGETS:
  Minimum 44 × 44 dp untuk semua interactive element
  Icon-only buttons: 48 × 48 dp minimum
  Chat bubbles: full-width, easy to tap for expansion

TEXT SIZES:
  No hardcoded font sizes — semua menggunakan scalable units
  Respect system font size settings
  Text akan scale dengan user's accessibility font setting
  Minimum readable: 14sp base, heading 18sp

COLOR CONTRAST:
  Text on white: ≥ 4.5:1 (WCAG AA)
  Text on colored badge: ≥ 3:1 (WCAG AA Large Text)
  Classification badge colors tested for colorblind safety:
    PUBLIC:              hijau (#2E7D32)
    INTERNAL:            biru (#1565C0)
    CONFIDENTIAL:        oranye (#E65100)
    STRICTLY_CONF:       merah (#B71C1C)
  Tidak menggunakan warna saja sebagai satu-satunya indicator
  (selalu ada text label + icon)

SCREEN READER SUPPORT:
  Semua gambar: accessibilityLabel
  Semua ikon: accessibilityLabel + accessibilityRole="button"
  Loading states: accessibilityLiveRegion="polite"
  Error states: accessibilityLiveRegion="assertive"
  Streaming text: accessibilityLiveRegion="polite" (announce new tokens)
  Graph view: accessibility description untuk nodes dan edges

ANIMATION CONTROL:
  Respect reduceMotion system setting
  Jika reduceMotion = true:
    → Disable token-by-token streaming animation
    → Show full response setelah LLM selesai (batch reveal)
    → Disable slide/fade animations pada navigation

KEYBOARD / HARDWARE KEYBOARD:
  Support untuk external keyboard (tablet use case)
  Tab navigation di form fields
  Enter key: submit search query / send message
```

---

## RECOMMENDATION

### REC-01 — Screen Priority untuk MVP

```
Wave 1 — Core (Launch MVP):
  ✓ LoginScreen
  ✓ DashboardScreen
  ✓ SearchScreen + SearchResultScreen
  ✓ AssistantChatScreen (dengan streaming)
  ✓ CitationFullViewModal
  ✓ DocumentListScreen
  ✓ DocumentDetailScreen (tab: INFO + DOKUMEN)
  ✓ DocumentUploadScreen

Wave 2 — Feature Complete:
  ○ OcrReviewScreen
  ○ ChunkExplorerScreen
  ○ RelationshipGraphScreen
  ○ RegulationExplorerModal
  ○ ConversationListScreen

Wave 3 — Admin & Enhancement:
  ○ AuditScreen
  ○ AdminScreen
  ○ PasalDetailModal (granular)
  ○ Export functionality
  ○ Notification system
```

### REC-02 — SSE di React Native perlu Polyfill
EventSource tidak native di React Native (hanya browser). Gunakan
`react-native-sse` package atau implement custom SSE client via
`fetch()` dengan streaming body reader. Pastikan:
- Headers (Authorization) bisa di-attach
- POST method support (EventSource standar hanya GET)
- Reconnect logic manual
Rekomendasi: custom SSE client yang wraps fetch dengan ReadableStream.

### REC-03 — Relationship Graph adalah High-Complexity Component
Force-directed graph di React Native memerlukan:
- `react-native-svg` untuk render
- Custom force simulation (lihat D3 force layout atau custom implementation)
- Gesture handler untuk pan + zoom
- Performa concern: limit nodes ke 20 untuk mobile
Jika terlalu berat: fallback ke list view "Dokumen Terkait" yang simpler.
Rekomendasi: list view dulu di Wave 1, graph di Wave 2.

### REC-04 — PDF Viewer Security
`react-native-pdf` mengunduh file ke temp storage lokal.
Untuk dokumen CONFIDENTIAL dan STRICTLY_CONFIDENTIAL:
- Gunakan in-memory rendering jika memungkinkan
- Clear temp file setelah user navigates away
- Disable screenshot (hanya Android, via FLAG_SECURE)
- Disable text selection untuk STRICTLY_CONFIDENTIAL (cegah copy-paste)

### REC-05 — Ambiguity: Regulation Tree Performance
REGULASI panjang (UU UUPA, UU HT, KUHP) bisa memiliki ratusan pasal.
`GET /regulations/{id}/structure` yang mengembalikan full tree bisa sangat
berat. Dua opsi:
1. **Lazy loading**: load BAB list dulu, expand per-BAB on demand
2. **Virtual list**: render hanya visible nodes

**Rekomendasi**: Lazy loading (Option 1) — lebih sederhana, UX familiar
(accordion pattern). Konfirmasi sebelum implementasi.

### REC-06 — Ambiguity: Dark Mode Support
Belum ada keputusan soal dark mode. Opsi:
1. **Light-only**: lebih sederhana, cukup untuk internal tool
2. **System-adaptive dark mode**: mengikuti device setting
3. **User-selectable**: toggle di Profile screen

Mengingat ini internal tool notaris (kemungkinan digunakan di lingkungan
kantor dengan cahaya normal), **light-only untuk awal** adalah rekomendasi.
Konfirmasi sebelum styling system dibangun.

---

## STATUS

```
STEP 1 — ANALYZE NEW DOMAIN         ✅ COMPLETE
STEP 2 — DDL DESIGN                 ✅ COMPLETE
STEP 3 — INGESTION & RETRIEVAL ARCH ✅ COMPLETE
STEP 4 — API & SERVICE ARCH         ✅ COMPLETE
STEP 5 — FRONTEND EXPERIENCE ARCH   ✅ COMPLETE (pending approval)
STEP 6 — BACKEND IMPLEMENTATION     ⏸ WAITING APPROVAL
STEP 7 — FRONTEND IMPLEMENTATION    ⏸ WAITING APPROVAL
STEP 8 — RAG PIPELINE IMPLEMENTATION⏸ WAITING APPROVAL
```

**2 Ambiguity untuk konfirmasi sebelum STEP 6 (implementasi):**

1. **Regulation Tree UX** → Lazy loading per-BAB (accordion) atau full tree sekaligus?
2. **Dark Mode** → Light-only, system-adaptive, atau user-selectable?

---

*Generated by: NOTARIST RAG PLATFORM — ANALYSIS_FIRST mode*
*File: /generated/docs/step5_frontend_experience_architecture.md*
*Date: 2026-05-23*
