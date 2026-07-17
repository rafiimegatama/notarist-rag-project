# 08 — Query Classification

| Field | Value |
|---|---|
| Status | IMPLEMENTED |
| Class | `com.notarist.search.application.routing.QueryClassifier` |
| Method | **Deterministic regex. No model.** |
| Tests | `QueryClassifierTest` — 29 cases |

---

## 1. Why classification is done by regex, not by a model

The classifier decides **whether a language model is allowed to answer at all**. That is a safety
decision, and a safety decision made by a probabilistic component is not a safety decision.

If an LLM-based router mis-classified *"apakah akta 125 sudah final"* as a semantic question — a
perfectly plausible mistake, since it is phrased like one — it would hand a **legal-status question to
a language model**, which is exactly the failure this whole sprint exists to remove. The router would
have become the vulnerability it was built to close.

So: patterns, in a fixed order, with deterministic parameter extraction. Slower to extend, impossible
to surprise.

> This is distinct from the pre-existing `IntentClassifier`, which classifies a query to **rank
> retrieval results**. That one is free to be fuzzy; nothing safety-critical depends on it. It is left
> untouched. (It remains partly dead code — see technical debt.)

---

## 2. The four categories

| Category | Meaning | LLM eligible? |
|---|---|---|
| `FACTUAL` | Counts, aggregations, deadlines | ❌ **never** |
| `STATUS` | Lifecycle/legal state of a specific entity | ❌ **never** |
| `SEMANTIC` | Similarity across documents | ✅ |
| `DOCUMENT_INTELLIGENCE` | Summarize, explain, compare | ✅ |
| `UNKNOWN` | Unclassified → grounded RAG fallback | ✅ |

`llmEligible` is a property **of the category enum itself**, not a decision made at call time. The
guard reads it. It is not possible to answer a `FACTUAL` question with a model without either changing
this enum or defeating the guard — both of which fail a test.

---

## 3. The ten subtypes

| Subtype | Category | Example |
|---|---|---|
| `STATISTICS` | FACTUAL | "berapa akta bulan ini" |
| `AGGREGATION` | FACTUAL | "berapa akta per jenis" |
| `REMINDER` | FACTUAL | "SKMHT jatuh tempo minggu depan" |
| `STATUS_LOOKUP` | STATUS | "apakah akta 125 sudah final" |
| `IDENTIFIER_LOOKUP` | STATUS | "akta nomor 125/VII/2024" |
| `SIMILARITY` | SEMANTIC | "klausul indemnity yang mirip" |
| `SUMMARIZE` | DOC_INTEL | "ringkas dokumen ini" |
| `EXPLAIN` | DOC_INTEL | "jelaskan klausul ini" |
| `COMPARISON` | DOC_INTEL | "bandingkan dua dokumen" |
| `OPEN_QUESTION` | UNKNOWN | "apa syarat sahnya perjanjian kredit" |

---

## 4. Evaluation order — the safety property

Order is not cosmetic. **It is the mechanism by which facts win ties.**

```
1. DEADLINE        → REMINDER
2. COUNTING        → STATISTICS | AGGREGATION (if grouped)
3. GROUPING        → AGGREGATION
4. STATUS          → STATUS_LOOKUP          ← before all semantic patterns
5. IDENTIFIER      → IDENTIFIER_LOOKUP
6. COMPARISON      → COMPARISON
7. SUMMARIZE       → SUMMARIZE
8. SIMILARITY      → SIMILARITY
9. EXPLAIN         → EXPLAIN
10. (fallback)     → OPEN_QUESTION
```

Three consequences worth stating explicitly:

**"jelaskan status akta 125 apakah sudah final"** contains an explanatory verb (`jelaskan` → EXPLAIN,
LLM-eligible) *and* a status question. Status is tested at step 4, EXPLAIN at step 9, so it routes to
**SQL**. Had the order been reversed, a legal-status question would quietly reach a language model.
Pinned by `QueryClassifierTest.explanatoryVerbDoesNotDefeatStatus()`.

**"berapa selisih jumlah akta bulan ini"** contains a comparison word (`selisih`) but asks for a
number. Counting is tested at step 2, comparison at step 6 → **FACTUAL**. A numeric difference is
arithmetic, not interpretation.

**"berapa SKMHT jatuh tempo minggu depan"** is both a count and a deadline. Deadline is tested first
because it is the narrower reading, and both are factual anyway — so the LLM is excluded either way.

---

## 5. Parameter extraction

All by regex, all deterministic — because these values end up in **SQL predicates**, and no
probabilistic component may touch a value that becomes a `WHERE` clause.

| Parameter | Extracted from | Example |
|---|---|---|
| `nomorAkta` | `akta no/nomor 125/VII/2024` | `125/VII/2024` |
| `timeWindow` | "bulan ini" / "hari ini" / "minggu depan" / "tahun ini" | `THIS_MONTH` |
| `jenisAkta` | APHT, SKMHT, FIDUSIA, ROYA, AJB, WASIAT, KUASA | `APHT` |
| `groupBy` | "per jenis" / "per status" | `JENIS_AKTA` |
| `statusTopic` | "gagal OCR", "gagal diproses" | `OCR_FAILURE` |
| `wantsList` | "tampilkan", "daftar", "cari", "temukan" | `true` |

`wantsList` is what separates *"show me similar clauses"* (retrieval, no LLM) from *"explain these
similar clauses"* (synthesis). When a lawyer asks to see a clause, they want the clause — not a
paraphrase that may have dropped the operative word.

---

## 6. Bahasa Indonesia first

The office works in Indonesian, so patterns lead with Indonesian and accept English as a secondary
form:

- counting: `berapa`, `jumlah`, `total`, `hitung`, `banyaknya`, `how many`
- status: `sudah`, `belum`, `telah`, `status`, `siapa yang menyetujui`, `is/has … finalized`
- deadline: `jatuh tempo`, `kadaluarsa`, `kedaluwarsa`, `tenggat`, `expire`, `deadline`
- similarity: `mirip`, `serupa`, `sejenis`, `similar`
- summarize: `ringkas`, `rangkum`, `intisari`, `summarize`
- compare: `bandingkan`, `perbedaan`, `selisih`, `compare`

Legal terms are kept in Indonesian throughout (`akta`, `SKMHT`, `APHT`, `fidusia`) — translating them
loses legal meaning.

---

## 7. Test coverage

29 cases in `QueryClassifierTest`, organised by the four specification categories:

| Group | Asserts |
|---|---|
| Category A — FACTUAL | counting, OCR failures, grouped counts, deadlines → **never LLM-eligible** |
| Category B — STATUS | all six phrasings → **never LLM-eligible** |
| Category C — SEMANTIC | list vs synthesis split |
| Category D — DOC INTEL | summarize / explain / compare → LLM-eligible |
| Tie-breaking | explanatory verb does not defeat status; numeric difference is not comparison |
| Parameters | time window, jenis akta, nomor akta, groupBy |
| Robustness | blank and `null` input do not throw |
