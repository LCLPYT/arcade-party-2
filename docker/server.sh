#!/usr/bin/env bash

set -euo pipefail

MC_VERSION="26.1.2"
FABRIC_VERSION="0.19.2"
SERVER_DIR="."
MODPACK_FILE="/preset/mods.mrpack"

# TODO also needs to be done when MC_VERSION or FABRIC_VERSION changed
if ! [ -f "$SERVER_DIR/fabric-server-launcher.jar" ]; then
  mrpack-install server fabric \
    --minecraft-version "$MC_VERSION" \
    --flavor-version "$FABRIC_VERSION" \
    --server-dir "$SERVER_DIR" \
    --server-file fabric-server-launcher.jar
fi

# TODO need a way to only do this when needed, e.g. when mods.mrpack checksum changed or if this wasn't executed yet
# TODO also needs to clear the mods/ directory before
mrpack-install "$MODPACK_FILE" --server-dir "$SERVER_DIR"

if [ "$EULA" = "true" ]; then
    echo "eula=true" > eula.txt
fi

# TODO sync contents from /preset/server into the current dir

: "${MAX_MEMORY:=2G}"

java "-Xmx$MAX_MEMORY" -jar fabric-server-launcher.jar --nogui
