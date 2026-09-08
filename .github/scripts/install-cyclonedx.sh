#!/bin/bash
set -euo pipefail

VERSION="${CDX_CLI_VERSION#v}"
CHECKSUM="${CDX_CLI_CHECKSUM}"

curl -sSfL --retry 5 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
  "https://github.com/CycloneDX/cyclonedx-cli/releases/download/v${VERSION}/cyclonedx-linux-x64" -o cyclonedx
sha256sum -c <(echo "${CHECKSUM} cyclonedx")

chmod +x cyclonedx
mkdir -p ~/.local/bin/
mv cyclonedx ~/.local/bin/cyclonedx
