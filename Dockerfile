FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY . .
RUN sed -i 's/\r//' gradlew && chmod +x gradlew && ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]