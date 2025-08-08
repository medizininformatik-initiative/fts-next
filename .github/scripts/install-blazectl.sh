#!/bin/bash
set -euo pipefail

VERSION="${BLAZECTL_VERSION#v}"

curl -sSfL "https://raw.githubusercontent.com/samply/blazectl/main/install.sh" | sh -s "${VERSION}"

mkdir -p ~/.local/bin/
mv blazectl ~/.local/bin/blazectl
