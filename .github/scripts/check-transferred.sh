#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if ! rd_hds_base_url="http://$(docker compose port rd-hds 8080)/fhir"; then
  >&2 echo "Unable to find research domain health data store URL"
  exit 2
fi

patientId="$(curl -sf "${rd_hds_base_url}/Patient" | jq -r .entry[0].resource.id)"
data="$(curl -sf "${rd_hds_base_url}/Patient/${patientId}/\$everything")"

function countResources() {
  echo "${data}" | jq "[.entry[].resource | select(.resourceType == \"${1}\")] | length"
}

echo "Check Transferred Patient Data"
assert "total bundle size" "$(echo "${data}" | jq .total)" "363"
assert "transferred patients count" "$(countResources "Patient")" "1"
assert "transferred observations count" "$(countResources "Observation")" "188"
assert "transferred conditions count" "$(countResources "Condition")" "9"
assert "transferred medications count" "$(countResources "Medication")" "17"
assert "transferred medication administration count" "$(countResources "MedicationAdministration")" "148"
