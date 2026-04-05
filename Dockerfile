# Java base image
FROM eclipse-temurin:17-jdk-alpine

# Working directory inside container
WORKDIR /app

# Copy jar file into container
COPY target/*.jar app.jar

# Expose application port
EXPOSE 8080

# Start the app
ENTRYPOINT ["java","-jar","app.jar"]
