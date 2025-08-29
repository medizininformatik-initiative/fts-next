#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if ! rd_hds_base_url="http://$(docker compose port rd-hds 8080)/fhir"; then
  >&2 echo "Unable to find research domain health data store URL"
  exit 2
fi

resultsFile=${1:-example.json}
baselineTimestamp=${2:-}

function countResources() {
  local resourceType=$1
  local url="${rd_hds_base_url}/${resourceType}?_summary=count${baselineTimestamp:+"&_lastUpdated=ge${baselineTimestamp}"}"
  
  curl -sf "${url}" | jq -r .total
}

function expectCount() {
  jq -r ".count.${1}" <"results/$resultsFile"
}

echo "Check Transferred Patient Data"
assert "transferred patients count" \
  "$(countResources "Patient")" "$(expectCount Patient)"

assert "transferred conditions count" \
  "$(countResources "Encounter")" "$(expectCount Encounter)"

assert "transferred observations count" \
  "$(countResources "Observation")" "$(expectCount Observation)"

assert "transferred conditions count" \
  "$(countResources "Condition")" "$(expectCount Condition)"

assert "transferred diagnostic reports count" \
  "$(countResources "DiagnosticReport")" "$(expectCount DiagnosticReport)"

assert "transferred medications count" \
  "$(countResources "Medication")" "$(expectCount Medication)"

assert "transferred medication administration count" \
  "$(countResources "MedicationAdministration")" "$(expectCount MedicationAdministration)"
