dependencies {
    implementation(project(":notarist-core"))

    // NOTE: deliberately does NOT depend on :notarist-ingest, :notarist-document or the OCR runtime.
    // OCR inference already happens elsewhere; this module only exposes the human REVIEW process after
    // extraction has completed. It reaches OCR output through its own landing tables (Flyway V12),
    // never by importing the pipeline. Kept additive on purpose.

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.swagger.annotations)

    testImplementation(libs.spring.boot.starter.test)
    // Real PostgreSQL for the repository/RLS/optimistic-locking integration tests. The migration
    // resources live in notarist-infra, so the test classpath pulls them in and runs the whole
    // Flyway chain (V1..V12) against the container — the same schema production migrates.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(project(":notarist-infra"))
}
