dependencies {
    implementation(project(":notarist-core"))

    // NOTE: deliberately does NOT depend on :notarist-review, :notarist-case or the OCR runtime.
    // The automatic checks consume OCR-review OUTPUT through an out-port (VerificationFactsPort) whose
    // adapter is filled by the composition root — this module never imports another bounded context,
    // and it never calls an LLM. Kept additive on purpose.

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.swagger.annotations)

    testImplementation(libs.spring.boot.starter.test)
    // Real PostgreSQL for the repository/RLS/optimistic-locking integration tests. The migration
    // resources live in notarist-infra, so the test classpath pulls them in and runs the whole
    // Flyway chain (V1..V13) against the container — the same schema production migrates.
    testImplementation(libs.testcontainers.postgresql)
    // Embedded PostgreSQL (real server binary, no Docker) for the event-orchestration integration test.
    testImplementation(libs.embedded.postgres)
    testImplementation(project(":notarist-infra"))
}
