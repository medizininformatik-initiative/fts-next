#!/bin/bash
set -euo pipefail

if [ -z "${1}" ]; then
  >&2 echo "PROJECT_NAME must be passed as first argument"
  exit 2
fi

if ! cd_agent_base_url="http://$(docker compose port cd-agent 8080)"; then
  >&2 echo "Unable to find clinical domain agent URL"
  exit 2
fi

curl -sf -X POST -w "%header{Content-Location}" "${cd_agent_base_url}/api/v2/process/${1}/start"
