#!/bin/bash
set -euo pipefail

VERSION="${BLAZECTL_VERSION#v}"
CHECKSUM="${BLAZECTL_CHECKSUM}"

url="https://github.com/samply/blazectl/releases/download/v${VERSION}/blazectl-${VERSION}-linux-amd64.tar.gz"
curl -sSfL "${url}" >blazectl.tar.gz
echo "${CHECKSUM} blazectl.tar.gz" | sha256sum -c

tar -xzf blazectl.tar.gz
mkdir -p ~/.local/bin/
mv blazectl ~/.local/bin/blazectl
