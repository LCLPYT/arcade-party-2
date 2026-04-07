FROM eclipse-temurin:21-jdk-alpine AS builder

ARG mc_version=1.21.11

RUN apk add --no-cache --update curl go && \
    go install github.com/packwiz/packwiz@latest

WORKDIR /deps

COPY docker/download_mrpack_install.sh ./

RUN ./download_mrpack_install.sh && mv mrpack-install /usr/bin/mrpack-install

# setup server
WORKDIR /packwiz

## download fabric server
RUN mkdir -p /server && \
    mrpack-install server fabric --minecraft-version ${mc_version} --server-dir /server --server-file fabric-server-launcher.jar

COPY docker/packwiz .

## install modpack
RUN ~/go/bin/packwiz modrinth export -o mods.mrpack && \
    mrpack-install mods.mrpack --server-dir /server

# build project
WORKDIR /project

COPY gradle gradle
COPY gradlew build.gradle gradle.properties settings.gradle LICENSE ./
COPY src src

RUN ./gradlew clean build --no-daemon \
    && rm build/libs/*-sources.jar

# separate stage for runtime image
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache su-exec bash rsync

# user setup (1000:1000)
RUN addgroup -g 1000 minecraft && \
    adduser -Ss /bin/false -u 1000 -G minecraft -h /home/minecraft minecraft

WORKDIR /template

# copy project-specific files
COPY docker/run-wrapper.sh docker/run.sh ./

# copy server files, mod artifact and server icon
COPY --from=builder /server .
COPY --from=builder /project/build/libs/*.jar mods/
COPY --from=builder /project/src/main/resources/assets/ap2/icon_64px.png server-icon.png

VOLUME ["/data"]
WORKDIR "/data"

EXPOSE 25565/tcp
EXPOSE 24454/udp

STOPSIGNAL SIGTERM
ENV UID=1000 GID=1000 LC_ALL=en_US.UTF-8

# run run wrapper script by default
ENTRYPOINT ["/template/run-wrapper.sh"]
CMD ["/template/run.sh"]
