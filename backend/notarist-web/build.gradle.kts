plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-auth"))
    implementation(project(":notarist-document"))
    implementation(project(":notarist-ingest"))
    implementation(project(":notarist-search"))
    implementation(project(":notarist-assistant"))
    implementation(project(":notarist-regulation"))
    implementation(project(":notarist-audit"))
    implementation(project(":notarist-infra"))
    implementation(project(":notarist-runtime"))
    implementation(project(":notarist-observability"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.ojdbc11)
    implementation(libs.postgresql)
    implementation(libs.liquibase.core)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.micrometer.prometheus)
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.springdoc.openapi.webmvc)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.oracle.xe)
    testImplementation(libs.archunit.junit5)
}
