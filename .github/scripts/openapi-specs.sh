#!/bin/bash
set -euo pipefail

if ! base_url="http://$(docker compose port ${1} 8080)"; then
  >&2 echo "Unable to find ${1}"
  exit 2
fi
echo "Base URL: ${base_url}"
echo "$(curl -sf "${base_url}/api/v2/projects")"

echo "Checking health"
echo "$(curl -svf --max-time 30 "${base_url}/actuator/health")"

echo "Fetching openapi..."
echo "$(curl -svf --max-time 30 "${base_url}/api/v2/openapi?format=json&prettyPrint=false")"

eval curl -sf "${base_url}/api/v2/openapi" | sed "s#${base_url}#http://${1}#g" > ${1}-openapi.json
