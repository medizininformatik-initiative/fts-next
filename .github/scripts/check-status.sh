#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if [ -z "${1:-}" ]; then
  >&2 echo "PROCESS_URL must be passed as first argument"
  exit 2
fi

status="$(curl -sf "${1}")"

echo "Check Transfer Result Status"
assert "phase" "$(echo "${status}" | jq -r .phase)" "COMPLETED"
assertGreaterOrEqual "bundles sent" "$(echo "${status}" | jq -r .bundlesSentCount)" "$(jq -r .bundlesSentCount <"results/${2:-100}.json")"
assert "patients skipped" "$(echo "${status}" | jq -r .patientsSkippedCount)" "$(jq -r .patientsSkippedCount <"results/${2:-100}.json")"
