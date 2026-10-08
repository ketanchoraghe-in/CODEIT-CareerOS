# Sprint 8 production image for the Spring Boot API.
# Multi-stage: Maven builds the jar, then only the JRE + jar ship.
# Configuration is 100% environment-based (see application-prod.yml);
# no secret is baked into this image.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /src/target/careeros-backend-*.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
