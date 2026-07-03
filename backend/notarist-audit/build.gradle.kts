dependencies {
    implementation(project(":notarist-core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.ojdbc11)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.oracle.xe)
    testImplementation(libs.archunit.junit5)
}
