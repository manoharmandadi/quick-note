FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package

FROM eclipse-temurin:21-jdk-alpine-3.23 AS runner

WORKDIR /app

COPY --from=builder ./app/target/patient-service-1.0-SNAPSHOT.jar ./app.jar

EXPOSE 4000

ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "app.jar"]