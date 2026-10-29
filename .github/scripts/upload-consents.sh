#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
export SCRIPT_DIR

requested="${1:-100}"
echo "Reading patient keys (requested=${requested})"
authored="./test-data/kds/authored.json"
keys=$(jq -rc "to_entries | .[0:${requested}] | .[].key" "${authored}")

count=$(printf '%s\n' "${keys}" | grep -c . || true)
if [ "${count}" -eq 0 ]; then
  >&2 echo "ERROR: no patient keys found in ${authored}"
  exit 1
fi
if [ "${count}" -lt "${requested}" ]; then
  >&2 echo "WARN: requested ${requested} keys, got ${count}"
fi

echo "Uploading consents for ${count} patients"
printf '%s\n' "${keys}" | xargs -P8 -I{} bash -c '${SCRIPT_DIR}/upload-consent.sh "{}"'

echo "Upload finished (${count} patients)"
