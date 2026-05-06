FROM eclipse-temurin:25-jdk-alpine-3.23 AS builder

ARG mc_version=26.1.2
ARG packwiz_commit=dfd8b68a4796c763e25bad50265ea1f1233e24f1

RUN apk add --no-cache --update curl go && \
    go install github.com/packwiz/packwiz@${packwiz_commit}

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
COPY src/lib/resources/ap2.accesswidener ./src/lib/resources/ap2.accesswidener

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew build --no-daemon \
    && rm build/libs/*-sources.jar

# separate stage for runtime image
FROM eclipse-temurin:25-jre-noble

RUN apt-get update && apt-get install -y --no-install-recommends gosu rsync && rm -rf /var/lib/apt/lists/*

# user setup (1000:1000)
RUN userdel -r $(getent passwd 1000 | cut -d: -f1) 2>/dev/null || true && \
    groupdel $(getent group 1000 | cut -d: -f1) 2>/dev/null || true && \
    groupadd -g 1000 minecraft && \
    useradd -s /bin/false -u 1000 -g minecraft -d /home/minecraft -m minecraft

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

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
  CMD bash -c 'echo > /dev/tcp/localhost/25565' || exit 1

STOPSIGNAL SIGTERM
ENV UID=1000 GID=1000 LC_ALL=en_US.UTF-8

# run run wrapper script by default
ENTRYPOINT ["/template/run-wrapper.sh"]
CMD ["/template/run.sh"]
