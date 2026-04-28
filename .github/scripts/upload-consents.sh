#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
export SCRIPT_DIR

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="MioAzTLMjzbPNyx"

export share

requested="${1:-100}"
echo "Fetching patient keys (requested=${requested})"
keys=$(curl -sSLf --retry 3 -u "${share}:" "${base_url}/authored.json" \
  | jq -rc "to_entries | .[0:${requested}] | .[].key")

count=$(printf '%s\n' "${keys}" | grep -c . || true)
if [ "${count}" -eq 0 ]; then
  >&2 echo "ERROR: no patient keys retrieved from ${base_url}/authored.json"
  exit 1
fi
if [ "${count}" -lt "${requested}" ]; then
  >&2 echo "WARN: requested ${requested} keys, got ${count}"
fi

echo "Uploading consents for ${count} patients"
printf '%s\n' "${keys}" | xargs -P8 -I{} bash -c '${SCRIPT_DIR}/upload-consent.sh "{}"'

echo "Upload finished (${count} patients)"
