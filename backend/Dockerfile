# syntax=docker/dockerfile:1
# BookHive backend image.  Build from the repo root:  docker build -t bookhive-backend backend

# ---------- Stage 1: build the jar (JDK + Gradle, thrown away afterwards) ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Build files first: this layer (and the downloaded dependencies) is cached
# until build.gradle.kts changes, so code-only changes rebuild much faster.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# Tests run in CI, not during the image build
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/bookhive-*.jar application.jar

# Split the jar into layers, from least to most often changed:
# dependencies -> spring-boot-loader -> snapshot-dependencies -> application
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Stage 2: run (JRE only, no build tools or source code) ----------
FROM eclipse-temurin:25-jre-alpine

# Never run as root: if the app is ever exploited, the attacker isn't root in the container
RUN addgroup -S bookhive && adduser -S bookhive -G bookhive
WORKDIR /app

# One COPY per layer: a code change only replaces the small "application" layer
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER bookhive
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

# Use up to 75% of the container's memory limit for the Java heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
