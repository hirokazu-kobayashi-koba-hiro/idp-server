# -------- Step 1: Build Stage --------
#FROM gradle:8.14-jdk21-alpine AS builder
#
#WORKDIR /workspace
#
## cache
#COPY settings.gradle build.gradle /workspace/
#COPY gradle /workspace/gradle
#
## copy module
#COPY libs /workspace/libs
#COPY app /workspace/app
#
#RUN gradle clean build -x test --no-daemon

# -------- Step 2: Runtime Stage --------
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# app/build/libs/app-x.x.x.jar
COPY ./app/build/libs/idp-server-*.jar /app/idp-server.jar
COPY entrypoint.sh /app/entrypoint.sh
COPY plugins /app/plugins

RUN chmod +x /app/entrypoint.sh && \
    addgroup -S idpserver && \
    adduser -S idpserver -G idpserver && \
    chown -R idpserver:idpserver /app

USER idpserver

ENTRYPOINT ["/app/entrypoint.sh"]
