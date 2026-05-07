#!/bin/bash
# Upload consents for a contiguous slice of the test-data patient list.
# Used by measure-e2e.sh to add only the delta of patients beyond what is
# already consented in gICS — avoids the duplicate-consent inflation that
# upload-consents.sh would cause when called repeatedly with growing N.
#
# Usage: upload-consents-range.sh START END
#   START — inclusive index into authored.json (0-based)
#   END   — exclusive index
#
# Example: upload-consents-range.sh 100 1000  # uploads keys [100..999]
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
export SCRIPT_DIR

if [ -z "${1:-}" ] || [ -z "${2:-}" ]; then
  >&2 echo "Usage: $(basename "$0") START END"
  exit 2
fi

start="${1}"
end="${2}"

if [ "${end}" -le "${start}" ]; then
  echo "upload-consents-range: empty range [${start}, ${end}) — nothing to upload"
  exit 0
fi

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="MioAzTLMjzbPNyx"
export share

echo "Fetching patient keys [${start}:${end})"
keys=$(curl -sSLf --retry 3 -u "${share}:" "${base_url}/authored.json" \
  | jq -rc "to_entries | .[${start}:${end}] | .[].key")

count=$(printf '%s\n' "${keys}" | grep -c . || true)
expected=$((end - start))
if [ "${count}" -eq 0 ]; then
  >&2 echo "ERROR: no patient keys retrieved from ${base_url}/authored.json for range [${start}:${end})"
  exit 1
fi
if [ "${count}" -lt "${expected}" ]; then
  >&2 echo "WARN: requested ${expected} keys, got ${count} — share may have fewer entries than END"
fi

echo "Uploading consents for ${count} patients"
printf '%s\n' "${keys}" | xargs -P8 -I{} bash -c '${SCRIPT_DIR}/upload-consent.sh "{}"'

echo "Upload finished (${count} patients, range [${start}:${end}))"
