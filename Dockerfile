# -------- Step 1: Build Stage --------
FROM gradle:8.5-jdk21 AS builder

WORKDIR /workspace

# cache
COPY settings.gradle build.gradle /workspace/
COPY gradle /workspace/gradle

# copy module
COPY libs /workspace/libs
COPY app /workspace/app

RUN gradle clean build -x test --no-daemon

# -------- Step 2: Runtime Stage --------
FROM openjdk:21-slim AS runtime

WORKDIR /app

# app/build/libs/app-x.x.x.jar
COPY --from=builder /workspace/app/build/libs/app-1.0.0.jar /app/idp-server.jar
COPY entrypoint.sh /app/entrypoint.sh
COPY plugins /app/plugins

RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
