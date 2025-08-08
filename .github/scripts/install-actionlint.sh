#!/bin/bash
set -euo pipefail

VERSION="${ACTIONLINT_VERSION#v}"
CHECKSUM="${ACTIONLINT_CHECKSUM}"

curl -sSfL "https://github.com/rhysd/actionlint/releases/download/v${VERSION}/actionlint_${VERSION}_linux_amd64.tar.gz" | tar -xz
sha256sum -c <(echo "${CHECKSUM} actionlint")

mkdir -p ~/.local/bin/
mv actionlint ~/.local/bin/actionlint
