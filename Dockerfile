# Use an official Gradle image to build the project
FROM gradle:8.3-jdk20 AS builder
WORKDIR /app

# Copy Gradle wrapper and project files
COPY build.gradle.kts settings.gradle.kts /app/
COPY gradle /app/gradle
COPY src /app/src

# Build the project using the shadowJar task
RUN gradle shadowJar --no-daemon

# Use a lightweight JDK image to run the application
FROM eclipse-temurin:20-jre
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]