#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
export SCRIPT_DIR

share="https://speicherwolke.uni-leipzig.de/index.php/s/MioAzTLMjzbPNyx"

export share

echo "Uploading test data files"
curl -sf "${share}/download?files=authored.json" | jq -rc "to_entries | .[0:${1:-100}] | .[].key" \
| xargs -P8 -I{} bash -c  '${SCRIPT_DIR}/upload-consent.sh "{}"'

echo "Upload finished"
