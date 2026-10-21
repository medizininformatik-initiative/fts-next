#!/bin/bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  >&2 echo "PROJECT_NAME must be passed as first argument"
  exit 2
fi

if ! cd_agent_base_url="http://$(docker compose port cd-agent 8080)"; then
  >&2 echo "Unable to find clinical domain agent URL"
  exit 2
fi

share="${2}"
limit="${3:-100}"
authored="./test-data/${share}/authored.json"

if [ ! -f "${authored}" ]; then
  >&2 echo "${authored} not found, run make download-test-data"
  exit 2
fi

available=$(jq length "${authored}")
if [ "${limit}" -gt "${available}" ]; then
  >&2 echo "WARN: requested ${limit} patients, test data holds ${available}"
fi

jq -c "[to_entries | .[0:$limit] | .[].key]" "${authored}" \
| curl -sSf --data @- -H "Content-Type: application/json" \
    -w "%header{Content-Location}" "${cd_agent_base_url}/api/v2/process/${1}/start"
