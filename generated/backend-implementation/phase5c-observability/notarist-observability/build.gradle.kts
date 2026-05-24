// notarist-observability — cross-cutting observability: health, metrics, tracing, circuit breaker
// Self-contained: no cross-module business logic imports.
// Depends only on notarist-core for shared value objects and constants.
dependencies {
    implementation(project(":notarist-core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)
    implementation(libs.resilience4j.spring.boot3)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
