dependencies {
    implementation(project(":notarist-core"))
    // Needed to read real degradation state from DegradedModeRegistry (see
    // OperationalDegradationHierarchy / HealthAggregationService). notarist-infra does not
    // depend on notarist-observability, so this direction is not circular.
    implementation(project(":notarist-infra"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    // Needed for @PreAuthorize on OperationalHealthEndpoint. The /ops/** surface (DLQ replay,
    // reindex) is destructive and cross-tenant by nature, so its authorization is asserted on the
    // METHOD, not only on a URL matcher in notarist-web — a URL matcher silently stops protecting
    // an endpoint the moment someone changes its path or adds a new one to the class.
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.postgresql)
    implementation(libs.micrometer.prometheus)
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
}
