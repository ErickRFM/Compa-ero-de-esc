# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY services ./services

RUN chmod +x ./gradlew \
    && ./gradlew :services:api:installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN useradd --system --create-home --uid 10001 appuser

COPY --from=build /workspace/services/api/build/install/api/ /app/

ENV API_HOST=0.0.0.0
EXPOSE 8080

USER appuser

CMD ["sh", "-c", "API_PORT=${API_PORT:-${PORT:-8080}} exec /app/bin/api"]
