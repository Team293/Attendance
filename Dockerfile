# Use a Gradle version that supports Java 21
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts /app/
COPY gradle /app/gradle
COPY src /app/src

# (Optional but recommended) ensure toolchain is 21 via build.gradle.kts (see below)
RUN gradle --version
RUN gradle shadowJar --no-daemon

# Run with JRE 21
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
