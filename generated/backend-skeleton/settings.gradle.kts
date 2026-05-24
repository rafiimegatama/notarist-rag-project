rootProject.name = "notarist-rag"

include(
    "notarist-core",
    "notarist-auth",
    "notarist-document",
    "notarist-ingest",
    "notarist-search",
    "notarist-assistant",
    "notarist-regulation",
    "notarist-audit",
    "notarist-infra",
    "notarist-runtime",
    "notarist-observability",
    "notarist-web"
)

// Phase 5 / 5B / 5C modules: Java source lives in backend-implementation dirs.
// projectDir points Gradle to the correct root for each module's src/ and build.gradle.kts.
project(":notarist-infra").projectDir =
    file("../backend-implementation/phase5-infra-ai/notarist-infra")
project(":notarist-runtime").projectDir =
    file("../backend-implementation/phase5b-ai-runtime/notarist-runtime")
project(":notarist-observability").projectDir =
    file("../backend-implementation/phase5c-observability/notarist-observability")
