# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY backend/api-server/pom.xml backend/api-server/pom.xml
COPY backend/recommend-service/pom.xml backend/recommend-service/pom.xml
COPY backend/api-server/src backend/api-server/src
COPY backend/recommend-service/src backend/recommend-service/src

ARG MODULE
RUN mvn -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE
COPY --from=build /workspace/${JAR_FILE} /app/app.jar

EXPOSE 8080 8081 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
