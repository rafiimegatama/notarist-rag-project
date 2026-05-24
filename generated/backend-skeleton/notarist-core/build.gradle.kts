// notarist-core — shared kernel, zero Spring dependency in domain layer
dependencies {
    // Bean Validation API only (no Spring)
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    // Jackson for ApiResponse serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.spring.boot.starter.test)
}
