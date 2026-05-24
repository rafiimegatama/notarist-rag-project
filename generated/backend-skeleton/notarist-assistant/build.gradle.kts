dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-document"))
    implementation(project(":notarist-search"))
    implementation(project(":notarist-audit"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.reactor.core)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
