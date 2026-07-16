dependencies {
    implementation(project(":notarist-core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.postgresql)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
}
