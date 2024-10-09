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
echo "${status}"
assert-ge "number of bundles sent" "$(echo "${status}" | jq -r .sentBundles)" "$(jq -r .sentBundles <"results/${2:-100}.json")"
assert "number of patients skipped" "$(echo "${status}" | jq -r .skippedBundles)" "$(jq -r .skippedBundles <"results/${2:-100}.json")"
