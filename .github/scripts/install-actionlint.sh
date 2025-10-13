#!/bin/bash
set -euo pipefail

VERSION="${ACTIONLINT_VERSION#v}"
CHECKSUM="${ACTIONLINT_CHECKSUM}"

url="https://github.com/rhysd/actionlint/releases/download/v${VERSION}/actionlint_${VERSION}_linux_amd64.tar.gz"
curl -sSfL "${url}" >actionlint.tar.gz
echo "${CHECKSUM} actionlint.tar.gz" | sha256sum -c

tar -xzf actionlint.tar.gz
mkdir -p ~/.local/bin/
mv actionlint ~/.local/bin/actionlint
