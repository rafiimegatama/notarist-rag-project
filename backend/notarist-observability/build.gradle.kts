dependencies {
    implementation(project(":notarist-core"))
    // Needed to read real degradation state from DegradedModeRegistry (see
    // OperationalDegradationHierarchy / HealthAggregationService). notarist-infra does not
    // depend on notarist-observability, so this direction is not circular.
    implementation(project(":notarist-infra"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.postgresql)
    implementation(libs.micrometer.prometheus)
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
}
