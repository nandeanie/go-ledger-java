# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# --- runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1001 ledger
COPY --from=build /build/target/go-ledger-java-*.jar app.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
