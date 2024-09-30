#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

share="https://speicherwolke.uni-leipzig.de/index.php/s/MioAzTLMjzbPNyx"

if ! cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"; then
  >&2 echo "Unable to find clinical domain health data store URL"
  exit 2
fi

function transfer_test_data() {
  curl -sf "${share}/download?path=kds&files=${1}.json.gz" | gunzip \
  | curl -sf --data-binary @- -H "Content-Type: application/fhir+json" "${cd_hds_base_url}" \
  >/dev/null
}

transfer_test_data "hospitalInformation"
transfer_test_data "practitionerInformation"

echo "Uploading consent to gics and patients to cd-hds"
i=1
while read -r patient; do
  patient_id="$(echo "${patient}" | jq -r .key)"

  transfer_test_data "${patient_id}"

  printf "%s: %8d / %d\n" "${patient_id}" $((i++)) "${1:-10}"
done < <(curl -sf "${share}/download?files=authored.json" \
           | jq -c 'to_entries | .[]' | head -n "${1:-10}")
echo "Upload finished"
