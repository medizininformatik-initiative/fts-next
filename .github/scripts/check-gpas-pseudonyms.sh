#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if ! rd_hds_base_url="http://$(docker compose port rd-hds 8080)/fhir"; then
  >&2 echo "Unable to find research domain health data store URL"
  exit 2
fi

# gPAS identifiers are 9-digit numbers in our test domain
match='^[0-9]{9}$'

if curl -sSf "${rd_hds_base_url}/Patient" \
    | jq -r .entry[].resource.identifier[].value \
    | grep -Eq "${match}"; then
  echo "  OK ✅ All identifiers look like gPAS pseudonyms"
else
  echo "Fail ❌ One or more identifiers don't look like gPAS pseudonyms: "
  curl -sSf "${rd_hds_base_url}/Patient" \
      | jq -r .entry[].resource.identifier[].value \
      | grep -vE "${match}" | paste -sd,
  exit 1
fi
