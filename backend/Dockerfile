# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Install Maven directly (no mvnw needed, no curl needed)
RUN apk add --no-cache maven

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Build
COPY src ./src
RUN mvn -B -q -DskipTests package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.port=10000", \
  "-jar", "app.jar"]