dependencies {
    implementation(project(":notarist-core"))

    // NOTE: deliberately does NOT depend on :notarist-ingest.
    // The machine pipeline and the human workflow communicate by domain event only.
    // ArchUnit enforces this in both directions.

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)   // Micrometer MeterRegistry for case metrics
    implementation(libs.postgresql)
    implementation(libs.swagger.annotations)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
    // Real PostgreSQL for the repository/RLS/optimistic-locking integration tests. The migration
    // resources live in notarist-infra, so the test classpath pulls them in and runs the whole
    // Flyway chain (V1..V10) against the container — the same schema production migrates.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(project(":notarist-infra"))
}
