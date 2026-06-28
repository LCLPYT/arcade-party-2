#!/usr/bin/env bash

set -euo pipefail

MC_VERSION="26.2"
FABRIC_VERSION="0.19.3"
SERVER_DIR="."
MODPACK_FILE="/preset/mods.mrpack"
ARTIFACT_NAME="arcade-party-2"

# (Re-)install fabric server when versions change or jar is missing
VERSION_FILE="$SERVER_DIR/.fabric_install_version"
CURRENT_VERSION="$MC_VERSION:$FABRIC_VERSION"

if ! [ -f "$SERVER_DIR/fabric-server-launcher.jar" ] || \
   ! [ -f "$VERSION_FILE" ] || \
   [ "$(cat "$VERSION_FILE")" != "$CURRENT_VERSION" ]; then

  mrpack-install server fabric \
    --minecraft-version "$MC_VERSION" \
    --flavor-version "$FABRIC_VERSION" \
    --server-dir "$SERVER_DIR" \
    --server-file fabric-server-launcher.jar

  echo "$CURRENT_VERSION" > "$VERSION_FILE"
fi

# (Re-)install modpack when mods.mrpack checksum changes or first run
MRPACK_CHECKSUM=$(sha256sum "$MODPACK_FILE" | cut -d' ' -f1)
CHECKSUM_FILE="$SERVER_DIR/.mrpack_checksum"

if ! [ -f "$CHECKSUM_FILE" ] || [ "$(cat "$CHECKSUM_FILE")" != "$MRPACK_CHECKSUM" ]; then
  rm -rf "$SERVER_DIR/mods/"

  mrpack-install "$MODPACK_FILE" --server-dir "$SERVER_DIR"

  echo "$MRPACK_CHECKSUM" > "$CHECKSUM_FILE"
fi

# Sync preset server files (server icon, built mod jars) into working dir
rm -f "$SERVER_DIR"/mods/*"${ARTIFACT_NAME}"*.jar
rsync -a /preset/server/ "$SERVER_DIR/"

if [ "${EULA:-}" = "true" ]; then
    echo "eula=true" > eula.txt
fi

: "${MAX_MEMORY:=2G}"

java "-Xmx$MAX_MEMORY" -jar fabric-server-launcher.jar --nogui
