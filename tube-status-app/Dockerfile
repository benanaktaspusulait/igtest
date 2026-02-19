FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy build descriptors first for better layer caching.
COPY pom.xml pom.xml
COPY resilience-core/pom.xml resilience-core/pom.xml
COPY resilience-adapter-spring/pom.xml resilience-adapter-spring/pom.xml
COPY tube-status-app/pom.xml tube-status-app/pom.xml

RUN mvn -B -ntp -pl tube-status-app -am dependency:go-offline

COPY resilience-core/src resilience-core/src
COPY resilience-adapter-spring/src resilience-adapter-spring/src
COPY tube-status-app/src tube-status-app/src

RUN mvn -B -ntp -pl tube-status-app -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app app

COPY --from=build /workspace/tube-status-app/target/tube-status-app-0.0.1-SNAPSHOT.jar /app/app.jar

ENV SERVER_PORT=8080

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
