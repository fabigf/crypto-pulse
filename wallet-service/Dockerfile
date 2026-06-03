FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY market-feeder-service/pom.xml market-feeder-service/pom.xml
COPY wallet-service/pom.xml wallet-service/pom.xml
COPY order-processor-service/pom.xml order-processor-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
RUN mvn -pl wallet-service -am dependency:go-offline
COPY wallet-service/src wallet-service/src
RUN mvn -pl wallet-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/wallet-service/target/wallet-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
