#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if ! rd_hds_base_url="http://$(docker compose port rd-hds 8080)/fhir"; then
  >&2 echo "Unable to find research domain health data store URL"
  exit 2
fi

function countResources() {
  curl -sf "${rd_hds_base_url}/${1}?_summary=count" | jq -r .total
}

function expectCount() {
  jq -r ".count.${2}" <"results/${1}.json"
}

echo "Check Transferred Patient Data"
assert "transferred patients count" \
  "$(countResources "Patient")" "$(expectCount "${1:-10}" Patient)"

assert "transferred conditions count" \
  "$(countResources "Encounter")" "$(expectCount "${1:-10}" Encounter)"

assert "transferred observations count" \
  "$(countResources "Observation")" "$(expectCount "${1:-10}" Observation)"

assert "transferred conditions count" \
  "$(countResources "Condition")" "$(expectCount "${1:-10}" Condition)"

assert "transferred diagnostic reports count" \
  "$(countResources "DiagnosticReport")" "$(expectCount "${1:-10}" DiagnosticReport)"

assert "transferred medications count" \
  "$(countResources "Medication")" "$(expectCount "${1:-10}" Medication)"

assert "transferred medication administration count" \
  "$(countResources "MedicationAdministration")" "$(expectCount "${1:-10}" MedicationAdministration)"
