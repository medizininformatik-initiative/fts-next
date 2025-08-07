#!/bin/bash
set -euo pipefail

VERSION="${BLAZECTL_VERSION#v}"

wget -qO- "https://raw.githubusercontent.com/samply/blazectl/main/install.sh" | sh -s "$VERSION"

target="${HOME}/.local/bin/blazectl"
mkdir -p "$(dirname "${target}")"
mv blazectl "${target}"
chmod +x "${target}"
