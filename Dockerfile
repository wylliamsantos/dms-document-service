FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/dms-document-service.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
