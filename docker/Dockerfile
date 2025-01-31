FROM eclipse-temurin:21-alpine AS builder
WORKDIR /app

RUN apk add --update git

RUN git clone https://github.com/LCLPYT/arcade-party-2.git .

RUN ./gradlew clean remapJar --no-daemon

FROM itzg/minecraft-server:java21-alpine

COPY --from=builder /app/build/libs/*.jar /data/mods/