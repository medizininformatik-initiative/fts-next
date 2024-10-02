#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if ! gics_base_url="http://$(docker compose port gics 8080)/ttp-fhir/fhir/gics"; then
  >&2 echo "Unable to find gics URL"
  exit 2
fi

summary="$(curl -XPOST -sf -H "Content-Type: application/fhir+json" \
  --data '{"resourceType": "Parameters", "parameter": [{"name": "domain", "valueString": "MII"}]}' \
  "${gics_base_url}/\$allConsentsForDomain?_summary=count")"

echo "Check Consent Count"
assert "number of consents" "$(echo "${summary}" | jq -r .total)" "${1:-100}"
