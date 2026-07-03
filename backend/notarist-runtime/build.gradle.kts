dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-ingest"))
    implementation(project(":notarist-search"))
    implementation(project(":notarist-assistant"))
    implementation(project(":notarist-infra"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)
    implementation(libs.reactor.core)

    testImplementation(libs.spring.boot.starter.test)
}
