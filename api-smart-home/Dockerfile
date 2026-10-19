# Build the Spring Boot executable JAR with the project's Maven/Kotlin configuration.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# Run as an unprivileged user in a small JRE image.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=appuser:appuser /workspace/target/api-smart-home-0.0.1-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=4s --start-period=45s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
