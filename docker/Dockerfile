FROM eclipse-temurin:21-alpine AS builder

RUN apk add --update git

WORKDIR /app

COPY . .

RUN chmod +x gradlew && ./gradlew clean remapJar --no-daemon

FROM itzg/minecraft-server:java21-alpine

COPY --from=builder /app/build/libs/*.jar /data/mods/
COPY --from=builder /app/docker/packwiz /packwiz