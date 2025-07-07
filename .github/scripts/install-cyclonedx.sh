#!/bin/bash
set -euo pipefail

VERSION="${CDX_CLI_VERSION#v}"
CHECKSUM="${CDX_CLI_CHECKSUM}"

url="https://github.com/CycloneDX/cyclonedx-cli/releases/download/v${VERSION}/cyclonedx-linux-x64"
target="${HOME}/.local/bin/cyclonedx"
mkdir -p "$(dirname "${target}")"
wget "${url}" -qO cyclonedx
sha256sum -c <(echo "${CHECKSUM} cyclonedx")
mv cyclonedx "${target}"
chmod +x "${target}"
