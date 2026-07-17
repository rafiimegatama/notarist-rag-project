# 09 — Architecture Rules (ArchUnit)

| Field | Value |
|---|---|
| Status | IMPLEMENTED — **10 rules, all passing** |
| Location | `notarist-web/src/test/java/com/notarist/web/architecture/ArchitectureRulesTest.java` |
| Runs in | `./gradlew build` — a violation **fails the build** |

---

## 1. Why these exist

ArchUnit was already a declared test dependency in `notarist-search` and `notarist-assistant` — and
**had never been used. Zero rules existed.**

A rule that lives only in a design document is a rule that gets broken under deadline pressure:
quietly, by someone reasonable, wiring in "just one" convenient dependency at 6pm. Every rule below
protects a property the system's **correctness** rests on — not its tidiness.

---

## 2. The rules

### The answer-routing boundary (the point of this sprint)

| # | Rule | Protects |
|---|---|---|
| 1 | The assistant orchestrator must not depend on `LlmPort` / `LlmRequest` / `LlmResponse` | If the assistant can reach the LLM directly, the router can be bypassed and the `FactualQueryGuard` with it. Facts would drift back to being answered by a model. |
| 2 | The assistant must not depend on JDBC / JPA / EntityManager | SQL belongs to the strategies that own it. The assistant **renders** answers; it does not fetch facts. |
| 3 | **Non-LLM strategies must not hold `RagPort`** | This is what makes "a factual strategy cannot call a model" true **by construction** rather than by discipline. |
| 4 | Only `runtime` / `infra` may touch a concrete provider (Ollama, Paddle, Qdrant) | The runtime owns timeouts, cancellation, queue isolation and degradation. Bypassing it forfeits all of them. |

> **Rule 3 caught a real mistake during this sprint.** My first exclusion regex was
> `.*(DocumentQa|Hybrid|Comparison)Strategy`, which does not match `HybridSearchStrategy` (the word
> "Search" sits in the middle). The rule failed, correctly, and named the violating constructor. Two
> observations: the rule works, and `SemanticSearchStrategy` did **not** appear in the violation list —
> proving it genuinely holds no path to a model.

### Boundaries defended *before* the modules exist

| # | Rule | Protects |
|---|---|---|
| 5 | `..qc..` must not depend on `..runtime..` or `..assistant..` | **QC determinism.** Same draft + same facts + same ruleset ⇒ same verdict, always. A probabilistic QC gate manufactures false confidence and is worse than none. |
| 6 | `..kase..` must not depend on `..ingest..` | The Case must not block on worker mechanics. |
| 7 | `..ingest..` must not depend on `..kase..` | The pipeline must never know what a Case is. |

Rules 5–7 pass **vacuously** today (`allowEmptyShould(true)`) because those modules do not exist yet.
That is deliberate. Adding a boundary rule *after* the module is written means adding it after the
first violation is already in — and then it never gets added, because fixing it is now "a refactor".
Written now, they start enforcing on the day the first class lands.

Rules 6 and 7 together are the constraint the entire Case architecture rests on: the human workflow and
the machine pipeline communicate **only** by domain event, and must never re-fuse.

### General hygiene

| # | Rule |
|---|---|
| 8 | The domain layer must not depend on infrastructure |
| 9 | `notarist-core` must not depend on any other `notarist` module |
| 10 | **No cyclic dependencies between modules** |

---

## 3. A trap worth documenting

The first version of the test imported classes with:

```java
.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)   // ← WRONG
```

Sibling modules reach `notarist-web` **as JARs**, so this imported nothing but `notarist-web` itself.
**Every rule then failed with "failed to check any classes"** — and had ArchUnit's default been to pass
on empty instead of fail, all ten rules would have reported green while checking absolutely nothing.

That is the worst possible failure mode for a safety net: a permanently green test that verifies
nothing. The fix is to keep JARs and scope the import to our own packages:

```java
classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.notarist");
```

If someone later "cleans up" that import option, the rules go quiet rather than red. Worth a comment in
the file — there is one.

---

## 4. Result

```
ArchitectureRulesTest: 10 tests, 0 failures

✓ the assistant orchestrator must not touch an LLM directly — it goes through the router
✓ the assistant must never reach SQL directly
✓ non-LLM strategies must not hold the RAG port
✓ the LLM is reachable only through the runtime's ports, never a concrete provider
✓ Quality Control must never depend on the AI runtime — QC verdicts are deterministic
✓ Case must never depend on Ingest — the human workflow and machine pipeline stay apart
✓ Ingest must never depend on Case — the pipeline must not know what a Case is
✓ the domain layer must not depend on infrastructure
✓ notarist-core must not depend on any other notarist module
✓ there are no cyclic dependencies between modules
```

The forbidden dependencies from the sprint brief are now **compile-time failures**, not conventions.
