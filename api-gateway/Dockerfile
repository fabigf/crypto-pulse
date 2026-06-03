FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY market-feeder-service/pom.xml market-feeder-service/pom.xml
COPY wallet-service/pom.xml wallet-service/pom.xml
COPY order-processor-service/pom.xml order-processor-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
RUN mvn -pl api-gateway -am dependency:go-offline
COPY api-gateway/src api-gateway/src
RUN mvn -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
