FROM gradle:8.7-jdk17 AS builder

WORKDIR /home/gradle/src
COPY . .

RUN gradle shadowJar --no-daemon
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /home/gradle/src/build/libs/discord-alert-consumer-1.0.0.jar ./app.jar
CMD ["java", "-jar", "app.jar"]