FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q clean package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S audittrove && adduser -S audittrove -G audittrove
WORKDIR /app
COPY --from=build /workspace/target/audittrove.jar app.jar
USER audittrove
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
