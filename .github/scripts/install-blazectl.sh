#!/bin/bash
set -euo pipefail

VERSION="${BLAZECTL_VERSION#v}"
CHECKSUM="${BLAZECTL_CHECKSUM}"

url="https://github.com/samply/blazectl/releases/download/v$VERSION/blazectl-$VERSION-linux-amd64.tar.gz"
target="${HOME}/.local/bin/blazectl"
mkdir -p "$(dirname "${target}")"
wget "${url}" -qO- | tar -xz
sha256sum -c <(echo "${CHECKSUM} blazectl")
mv blazectl "${target}"
chmod +x "${target}"
