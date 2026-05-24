// notarist-infra — infrastructure adapters for Qdrant, MinIO, PostgreSQL, Oracle
// Implements ports from notarist-ingest (VectorIndexPort, DocumentStoragePort)
// and notarist-search (VectorSearchPort).
// Does NOT depend on notarist-runtime or notarist-observability.
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-ingest"))
    implementation(project(":notarist-search"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.minio)
    implementation(libs.qdrant.client)
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
