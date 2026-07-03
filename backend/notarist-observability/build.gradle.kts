dependencies {
    implementation(project(":notarist-core"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.micrometer.prometheus)
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)

    testImplementation(libs.spring.boot.starter.test)
}
