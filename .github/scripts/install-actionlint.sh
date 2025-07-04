#!/bin/bash
set -euo pipefail

VERSION="${ACTIONLINT_VERSION#v}"
CHECKSUM="${ACTIONLINT_CHECKSUM}"

url="https://github.com/rhysd/actionlint/releases/download/v${VERSION}/actionlint_${VERSION}_linux_amd64.tar.gz"
target="${HOME}/.local/bin/actionlint"
mkdir -p "$(dirname "${target}")"
wget "${url}" -qO- | tar -xz
sha256sum -c <(echo "${CHECKSUM} actionlint")
mv actionlint "${target}"
chmod +x "${target}"
