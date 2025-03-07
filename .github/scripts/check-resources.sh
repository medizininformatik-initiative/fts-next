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
  jq -r ".count.${2}" <"results/${1}"
}

echo "Check Transferred Patient Data"
assert "transferred patients count" \
  "$(countResources "Patient")" "$(expectCount "${2:-example.json}" Patient)"

assert "transferred conditions count" \
  "$(countResources "Encounter")" "$(expectCount "${2:-example.json}" Encounter)"

assert "transferred observations count" \
  "$(countResources "Observation")" "$(expectCount "${2:-example.json}" Observation)"

assert "transferred conditions count" \
  "$(countResources "Condition")" "$(expectCount "${2:-example.json}" Condition)"

assert "transferred diagnostic reports count" \
  "$(countResources "DiagnosticReport")" "$(expectCount "${2:-example.json}" DiagnosticReport)"

assert "transferred medications count" \
  "$(countResources "Medication")" "$(expectCount "${2:-example.json}" Medication)"

assert "transferred medication administration count" \
  "$(countResources "MedicationAdministration")" "$(expectCount "${2:-example.json}" MedicationAdministration)"
