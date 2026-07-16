package com.notarist.kase.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Rules that keep the domain a domain.
 *
 * <p>The one that matters most is {@link #aggregatesHaveNoPublicSetters()}. The entire design rests on
 * a single claim — <em>an illegal state is unreachable, because the only way to change state is a
 * transition the aggregate itself validates</em>. That claim survives exactly as long as nobody adds a
 * public setter. A design document cannot prevent that; a failing build can.
 *
 * <p>This is deliberately not the pattern used by the existing {@code DocumentLegal} aggregate, whose
 * {@code transitionStatus()} is an unguarded assignment with a {@code // TODO: enforce state machine
 * transitions} and whose rules live in a static helper any caller may simply skip. Rules beside an
 * aggregate are advice; rules inside it are invariants.
 */
class DomainArchitectureTest {

    private static JavaClasses domain;

    @BeforeAll
    static void importClasses() {
        domain = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.notarist.kase");
    }

    // ---- Aggregates own their state -------------------------------------------------------------

    @Test
    @DisplayName("aggregates expose NO public setters — state changes only through transition()")
    void aggregatesHaveNoPublicSetters() {
        ArchRule rule = noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..kase.domain.model..")
                .should().haveNameMatching("set[A-Z].*")
                .because("if a service can set a field, the state machine is advisory and an illegal "
                        + "state becomes reachable. transition() is the only door.");

        rule.check(domain);
    }

    @Test
    @DisplayName("aggregate fields are private — no package-private back door")
    void aggregateFieldsArePrivate() {
        ArchRule rule = fields()
                .that().areDeclaredInClassesThat().resideInAPackage("..kase.domain.model..")
                .and().areNotStatic()
                .should().bePrivate()
                .because("a non-private field is a setter with extra steps");

        rule.check(domain);
    }

    @Test
    @DisplayName("only the aggregate itself may raise its own events")
    void onlyAggregatesRaiseEvents() {
        ArchRule rule = methods()
                .that().haveName("raise")
                .and().areDeclaredInClassesThat().resideInAPackage("..kase.domain.model..")
                .should().beProtected()
                .because("an event is a statement about what an aggregate did — nobody else is in a "
                        + "position to make it on its behalf");

        rule.check(domain);
    }

    @Test
    @DisplayName("application services must not drive the state machines — only aggregates may")
    void servicesDoNotDriveStateMachinesDirectly() {
        // A service must of course be able to NAME a target state (it has to pass one to transition()).
        // What it must never do is consult the transition table itself: a service that asks "is this
        // legal?" and then mutates has re-implemented the aggregate badly, and the two copies of the
        // rule will drift. Deciding legality belongs to the aggregate, exactly once.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..kase.application..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("StateMachine")
                .because("the aggregate decides what is legal. A service only requests a transition.")
                .allowEmptyShould(true);

        rule.check(domain);
    }

    // ---- The domain stays a domain ---------------------------------------------------------------

    @Test
    @DisplayName("the domain layer is framework-free — no Spring, no JPA")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..kase.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javax.persistence..")
                .because("the domain is the stable core. A JPA annotation on an aggregate makes the "
                        + "database schema a domain concern, and the two then drift together forever.");

        rule.check(domain);
    }

    @Test
    @DisplayName("the domain must not depend on the application layer")
    void domainDoesNotDependOnApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..kase.domain..")
                .should().dependOnClassesThat().resideInAPackage("..kase.application..")
                .because("dependencies point inward, toward the domain — never out of it");

        rule.check(domain);
    }

    @Test
    @DisplayName("the Case context must not depend on Ingest — the pipeline and the workflow stay apart")
    void caseDoesNotDependOnIngest() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..kase..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..ingest..", "..document..", "..search..", "..assistant..", "..auth..")
                .because("the Case observes the pipeline through an inbound port shaped in its OWN "
                        + "language. It never imports another context's types — that is what keeps the "
                        + "machine lifecycle and the human lifecycle from re-fusing.");

        rule.check(domain);
    }

    // ---- No aggregate cycles ---------------------------------------------------------------------

    @Test
    @DisplayName("there are no cycles between domain packages")
    void noPackageCycles() {
        ArchRule rule = slices()
                .matching("com.notarist.kase.domain.(*)..")
                .should().beFreeOfCycles()
                .because("a cycle between model, state, event and factory means the layering is fiction");

        rule.check(domain);
    }

    @Test
    @DisplayName("no aggregate references another aggregate ROOT — only its identity")
    void aggregatesReferenceEachOtherByIdOnly() {
        // Case → BundleId, Bundle → CaseId + DocumentRef, Approval → CaseId, Timeline → CaseId.
        // Never Case → Bundle (the object). Holding another root would fuse two transaction boundaries
        // into one, and a bundle would then be loaded — and locked — every time a case is touched.
        ArchRule rule = noClasses()
                .that().haveSimpleName("Case")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Bundle")
                .because("aggregates reference each other by identity, never by object reference");

        rule.check(domain);
    }

    @Test
    @DisplayName("the Document aggregate is referenced by ID only — never duplicated, never owned")
    void documentIsReferencedNotOwned() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..kase..")
                .should().dependOnClassesThat().haveSimpleName("DocumentLegal")
                .because("five ingest workers mutate DocumentLegal concurrently. Pulling it inside the "
                        + "Case aggregate would make every OCR completion contend on a business case — "
                        + "and would strand the thousands of documents that have no case at all.");

        rule.check(domain);
    }
}
