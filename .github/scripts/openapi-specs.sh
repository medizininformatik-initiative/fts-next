#!/bin/bash
set -euo pipefail

if ! base_url="http://$(docker compose port ${1} 8080)"; then
  >&2 echo "Unable to find ${1}"
  exit 2
fi

eval curl -sf "${base_url}/api/v2/openapi" | sed "s#${base_url}#http://${1}#g" > ${1}-openapi.json
