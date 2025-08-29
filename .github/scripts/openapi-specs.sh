#!/bin/bash
set -euo pipefail

if ! base_url="http://$(docker compose "${@:2}" port "${1}" 8080)"; then
  >&2 echo "Unable to find ${1}"
  exit 2
fi

exec curl -sSf "${base_url}/api/v2/openapi" | sed "s#${base_url}#http://${1}#g"
