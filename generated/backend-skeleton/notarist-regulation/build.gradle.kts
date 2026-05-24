dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-document"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.ojdbc11)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.oracle.xe)
    testImplementation(libs.archunit.junit5)
}
