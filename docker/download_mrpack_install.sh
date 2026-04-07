#!/usr/bin/env sh
set -e

VERSION="0.21.0-beta"
BASE_URL="https://github.com/nothub/mrpack-install/releases/download/v${VERSION}"

case "${TARGETARCH:-$(uname -m)}" in
    amd64|x86_64) FILE_ARCH="amd64" ;;
    arm64|aarch64) FILE_ARCH="arm64" ;;
    *) echo "Unsupported architecture: ${TARGETARCH:-$(uname -m)}" >&2 && exit 1 ;;
esac

URL="${BASE_URL}/mrpack-install_${VERSION}_linux_${FILE_ARCH}.tar.gz"

echo "Downloading $URL ..."

curl -fsSL "$URL" | tar -xz
