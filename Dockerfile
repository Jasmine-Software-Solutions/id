FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

COPY server/build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./

RUN gradle dependencies --no-daemon

COPY src/ src/

RUN gradle shadowJar --no-daemon

FROM openjdk:17

RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar
COPY --from=builder /app/src/main/resources/public/ public/

RUN mkdir -p /app/logs && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:${PORT:-80}/ || exit 1

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 