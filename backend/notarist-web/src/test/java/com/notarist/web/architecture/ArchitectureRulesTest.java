package com.notarist.web.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules, enforced at build time.
 *
 * <p>These exist because a rule that lives only in a design document is a rule that gets broken under
 * deadline pressure — quietly, by someone wiring in "just one" convenient dependency. Every rule here
 * protects a property the system's correctness actually rests on:
 *
 * <ul>
 *   <li><b>The assistant must not reach SQL or an LLM directly</b> — otherwise the answer router can
 *       be bypassed and factual questions drift back to being answered by a language model.</li>
 *   <li><b>Non-LLM strategies must not hold the RAG port</b> — this is what makes "a SQL strategy
 *       cannot call a model" true by construction rather than by good intentions.</li>
 *   <li><b>QC must never depend on the runtime</b> — QC verdicts must be deterministic.</li>
 *   <li><b>Case and Ingest must never depend on each other</b> — the human workflow and the machine
 *       pipeline must not re-fuse.</li>
 * </ul>
 *
 * <p>The QC/Case/Ingest rules are written now, before those modules exist, deliberately: they will
 * pass vacuously today and start protecting the boundary the moment the first class lands. Adding
 * them afterwards means adding them after the first violation is already in.
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        // Sibling modules arrive on notarist-web's classpath as JARs, so DO_NOT_INCLUDE_JARS would
        // import nothing but this module and every rule would pass (or fail) vacuously. Scoping the
        // import to com.notarist keeps third-party classes out without losing our own modules.
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.notarist");
    }

    // ---- The answer-routing boundary ------------------------------------------------------------

    @Test
    @DisplayName("the assistant orchestrator must not touch an LLM directly — it goes through the router")
    void assistantOrchestratorMustNotUseLlmPort() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..assistant.application.orchestrator..")
                .should().dependOnClassesThat()
                .haveNameMatching(".*LlmPort.*|.*LlmRequest.*|.*LlmResponse.*")
                .because("the assistant must ask the AnswerRouter for an answer, not choose an engine. "
                        + "If it can reach the LLM directly, the factual-query guard can be bypassed.");

        rule.check(classes);
    }

    @Test
    @DisplayName("the assistant must never reach SQL directly")
    void assistantMustNotUseSql() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..assistant..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.springframework.jdbc..",
                        "org.springframework.data.jpa..")
                .because("SQL access belongs to the strategies that own it. The assistant renders "
                        + "answers; it does not fetch facts.");

        rule.check(classes);
    }

    @Test
    @DisplayName("non-LLM strategies must not hold the RAG port")
    void deterministicStrategiesCannotReachTheLlm() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Strategy")
                .and().haveNameNotMatching(".*(DocumentQa|HybridSearch|Comparison)Strategy")
                .should().dependOnClassesThat().haveSimpleNameContaining("RagPort")
                .because("a factual strategy must have no code path to a language model. This is what "
                        + "makes the rule true by construction instead of by discipline.");

        rule.check(classes);
    }

    @Test
    @DisplayName("the LLM is reachable only through the runtime's ports, never a concrete provider")
    void noDirectProviderAccess() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..runtime..", "..infra..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..runtime.ollama..", "..runtime.ocr.provider..", "io.qdrant..")
                .because("providers are swappable; the runtime owns timeouts, cancellation, queue "
                        + "isolation and degradation. Bypassing it forfeits all of them.");

        rule.check(classes);
    }

    // ---- Boundaries defended before the modules exist -------------------------------------------

    @Test
    @DisplayName("Quality Control must never depend on the AI runtime — QC verdicts are deterministic")
    void qcMustNotDependOnRuntime() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..qc..")
                .should().dependOnClassesThat().resideInAnyPackage("..runtime..", "..assistant..")
                .because("same draft + same facts + same ruleset must always yield the same verdict. "
                        + "A probabilistic QC gate manufactures false confidence and is worse than none.")
                // notarist-qc does not exist yet — the rule passes vacuously today and starts
                // protecting the boundary the moment the first class lands.
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("Case must never depend on Ingest — the human workflow and machine pipeline stay apart")
    void caseMustNotDependOnIngest() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..kase..")
                .should().dependOnClassesThat().resideInAPackage("..ingest..")
                .because("the Case must not block on worker mechanics. They communicate by domain event.")
                .allowEmptyShould(true); // notarist-case does not exist yet — see above

        rule.check(classes);
    }

    @Test
    @DisplayName("Ingest must never depend on Case — the pipeline must not know what a Case is")
    void ingestMustNotDependOnCase() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..ingest..")
                .should().dependOnClassesThat().resideInAPackage("..kase..")
                .because("the pipeline echoes back the caseId it was handed; it never resolves one. "
                        + "A dependency here re-fuses the two lifecycles the design keeps apart.")
                .allowEmptyShould(true); // vacuous until notarist-case exists

        rule.check(classes);
    }

    // ---- General hygiene ------------------------------------------------------------------------

    @Test
    @DisplayName("the domain layer must not depend on infrastructure")
    void domainMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("the domain is the stable core; infrastructure is a detail that points inward.");

        rule.check(classes);
    }

    @Test
    @DisplayName("notarist-core must not depend on any other notarist module")
    void coreHasNoOutboundDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.notarist.core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.notarist.auth..", "com.notarist.document..", "com.notarist.ingest..",
                        "com.notarist.search..", "com.notarist.assistant..", "com.notarist.audit..",
                        "com.notarist.infra..", "com.notarist.runtime..", "com.notarist.web..")
                .because("core is shared by everything; a dependency out of it creates a cycle.");

        rule.check(classes);
    }

    @Test
    @DisplayName("there are no cyclic dependencies between modules")
    void noModuleCycles() {
        ArchRule rule = slices()
                .matching("com.notarist.(*)..")
                .should().beFreeOfCycles()
                .because("a cycle means the boundaries are fiction and the modules cannot be reasoned "
                        + "about — or extracted — independently.");

        rule.check(classes);
    }
}
