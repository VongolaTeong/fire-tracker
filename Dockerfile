# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first (cached layer) before copying sources, so code-only changes
# don't re-download the world.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/
# Tests use Testcontainers (Docker), which isn't available during a Render image build —
# CI already runs the full suite on every push.
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: slim JRE with just the jar ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Render injects PORT (the app reads ${PORT:8080}). MaxRAMPercentage keeps the JVM inside
# Render's 512 MB free tier.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
