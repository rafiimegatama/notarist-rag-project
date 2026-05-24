// notarist-runtime — AI runtime adapters (Ollama, bge-m3 embedding, PaddleOCR, reranker)
// Implements ports from notarist-assistant (LlmPort) and notarist-search (RerankerPort).
// Shared OCR contract (OcrPort) resolved from notarist-core.
// Does NOT depend on notarist-infra or notarist-ingest.
dependencies {
    implementation(project(":notarist-core"))
    implementation(project(":notarist-assistant"))
    implementation(project(":notarist-search"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.reactor.core)
    implementation(libs.okhttp)
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}
