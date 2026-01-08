# File: Dockerfile

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# copy artifact from build stage
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
