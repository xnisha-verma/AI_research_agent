# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml and fetch dependencies (to leverage Docker layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source files and build the jar package
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Create the lightweight runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user and group for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Render assigns port dynamically via the PORT env var; Spring Boot is configured to read it
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
