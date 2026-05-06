FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./
COPY domain/build.gradle.kts domain/build.gradle.kts
COPY interfaces/build.gradle.kts interfaces/build.gradle.kts
COPY server/build.gradle.kts server/build.gradle.kts

RUN --mount=type=secret,id=CODEARTIFACT_AUTH_TOKEN \
    export CODEARTIFACT_AUTH_TOKEN=$(cat /run/secrets/CODEARTIFACT_AUTH_TOKEN) && \
    ./gradlew --no-daemon :server:dependencies

COPY domain/src domain/src
COPY interfaces/src interfaces/src
COPY server/src server/src

RUN --mount=type=secret,id=CODEARTIFACT_AUTH_TOKEN \
    export CODEARTIFACT_AUTH_TOKEN=$(cat /run/secrets/CODEARTIFACT_AUTH_TOKEN) && \
    ./gradlew --no-daemon :server:shadowJar

FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

COPY --from=builder /app/server/build/libs/*-all.jar app.jar

RUN mkdir -p /app/logs && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:${PORT:-80}/ || exit 1

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
