# Render Docker 웹 서비스. 빌드는 Gradle wrapper(9.7.1), 실행은 JRE 21.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

RUN chmod +x gradlew \
    && ./gradlew bootJar -x test --no-daemon \
    && find build/libs -type f -name '*.jar' ! -name '*-plain.jar' -exec cp {} /out.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /out.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
