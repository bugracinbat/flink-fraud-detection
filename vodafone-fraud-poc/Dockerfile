FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests dependency:copy-dependencies compile

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency

EXPOSE 8080
CMD ["java", "-cp", "classes:dependency/*", "com.vodafone.poc.VodafoneFraudPoC"]
