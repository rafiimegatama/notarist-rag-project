# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────────────────────
# Notarist RAG backend — Cloud Run container image
#
# Multi-stage: build the Spring Boot fat jar with Gradle, then run it on a slim,
# non-root JRE. The image is self-contained — no localhost dependencies, no MinIO.
# Object storage is Google Cloud Storage via ADC (the Cloud Run runtime service
# account); Postgres is Supabase over TLS. Both are reached purely through env vars.
# ─────────────────────────────────────────────────────────────────────────────

# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Warm the Gradle cache on dependency descriptors first for better layer caching.
COPY gradle ./gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew

# Copy each module's build script, then the sources.
COPY notarist-core/build.gradle.kts            notarist-core/
COPY notarist-auth/build.gradle.kts            notarist-auth/
COPY notarist-document/build.gradle.kts        notarist-document/
COPY notarist-ingest/build.gradle.kts          notarist-ingest/
COPY notarist-search/build.gradle.kts          notarist-search/
COPY notarist-assistant/build.gradle.kts       notarist-assistant/
COPY notarist-regulation/build.gradle.kts      notarist-regulation/
COPY notarist-audit/build.gradle.kts           notarist-audit/
COPY notarist-infra/build.gradle.kts           notarist-infra/
COPY notarist-runtime/build.gradle.kts         notarist-runtime/
COPY notarist-observability/build.gradle.kts   notarist-observability/
COPY notarist-web/build.gradle.kts             notarist-web/
COPY . .

# Build only the runnable web module's bootJar; skip tests in the image build (CI runs them).
RUN ./gradlew :notarist-web:bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as a non-root user — Cloud Run does not require root and least-privilege is safer.
RUN groupadd --system notarist && useradd --system --gid notarist --home /app notarist

COPY --from=build /workspace/notarist-web/build/libs/notarist-web-*.jar /app/app.jar
RUN chown -R notarist:notarist /app
USER notarist

# Container listens on $PORT (Cloud Run injects it; default 8080). server.port honours $PORT.
ENV PORT=8080
EXPOSE 8080

# Container-aware heap sizing (respects the Cloud Run memory limit) and a fast, headless start.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
