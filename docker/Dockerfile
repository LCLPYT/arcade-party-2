FROM eclipse-temurin:21-alpine AS builder
WORKDIR /app

RUN apk add --update git

COPY gradle/ ./gradle/
COPY src/ ./src/
# .git is required for determining the project version
COPY .git ./.git/
COPY build.gradle gradle.properties gradlew settings.gradle LICENSE ./

RUN ./gradlew remapJar

FROM itzg/minecraft-server:java21-alpine

COPY --from=builder /app/build/libs/*.jar /data/mods/