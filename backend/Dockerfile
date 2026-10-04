# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so a source-only change reuses the cached layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user; uploaded images live in a volume owned by it.
RUN addgroup -S reloop && adduser -S reloop -G reloop \
    && mkdir -p /var/reloop/uploads \
    && chown -R reloop:reloop /var/reloop /app
COPY --from=build /app/target/*.jar app.jar
USER reloop

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
