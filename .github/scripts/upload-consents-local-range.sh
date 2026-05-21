#!/bin/bash
# Upload consents for a contiguous slice of a locally placed authored.json
# (instead of the speicherwolke share that upload-consents-range.sh uses).
# Same delta semantics as upload-consents-range.sh — see that file for the
# rationale behind range-based uploads.
#
# Usage: upload-consents-local-range.sh START END
#   START — inclusive index into LOCAL_AUTHORED_JSON (0-based)
#   END   — exclusive index
#
# Env:
#   LOCAL_AUTHORED_JSON  required; path to {patientId: date} map
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
  echo "upload-consents-local-range: empty range [${start}, ${end}) — nothing to upload"
  exit 0
fi

if [ -z "${LOCAL_AUTHORED_JSON:-}" ]; then
  >&2 echo "ERROR: LOCAL_AUTHORED_JSON must be set"
  exit 2
fi
if [ ! -f "${LOCAL_AUTHORED_JSON}" ]; then
  >&2 echo "ERROR: LOCAL_AUTHORED_JSON=${LOCAL_AUTHORED_JSON} not found"
  exit 2
fi

echo "Fetching patient keys [${start}:${end}) from ${LOCAL_AUTHORED_JSON}"
keys=$(jq -rc "to_entries | .[${start}:${end}] | .[].key" <"${LOCAL_AUTHORED_JSON}")

count=$(printf '%s\n' "${keys}" | grep -c . || true)
expected=$((end - start))
if [ "${count}" -eq 0 ]; then
  >&2 echo "ERROR: no patient keys retrieved from ${LOCAL_AUTHORED_JSON} for range [${start}:${end})"
  exit 1
fi
if [ "${count}" -lt "${expected}" ]; then
  >&2 echo "WARN: requested ${expected} keys, got ${count} — ${LOCAL_AUTHORED_JSON} may have fewer entries than END"
fi

echo "Uploading consents for ${count} patients"
printf '%s\n' "${keys}" | xargs -P8 -I{} bash -c '${SCRIPT_DIR}/upload-consent.sh "{}"'

echo "Upload finished (${count} patients, range [${start}:${end}))"
