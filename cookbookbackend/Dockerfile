FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/build/install/cookbookbackend/ ./
ENV COOKBOOK_HOST=0.0.0.0 PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:ActiveProcessorCount=2"
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/app/bin/cookbookbackend"]
