# ---- build stage ----
# gradle:jdk21 is a maintained tag; the gradle wrapper pins the exact 9.2.0 distribution.
FROM gradle:jdk21 AS build
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY gradlew ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew --no-daemon fatJar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Xmx300m -jar /app/app.jar"]