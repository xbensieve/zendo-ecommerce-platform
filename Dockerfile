# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S zendo && adduser -S zendo -G zendo

# Copy the built artifact from the builder stage
COPY --from=builder --chown=zendo:zendo /app/target/zendo-0.0.1-SNAPSHOT.jar app.jar

USER zendo

# Set conservative JVM limits for containers by default
# These can be overridden via JAVA_TOOL_OPTIONS in docker-compose.prod.yml
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+HeapDumpOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
