#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

share="https://speicherwolke.uni-leipzig.de/index.php/s/MioAzTLMjzbPNyx"

if ! cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"; then
  >&2 echo "Unable to find clinical domain health data store URL"
  exit 2
fi

function transfer_file() {
  curl -sf "${share}/download?path=kds&files=${1}.json.gz" | gunzip \
  | curl -sf --data-binary @- -H "Content-Type: application/fhir+json" "${cd_hds_base_url}" \
  >/dev/null
}
export -f transfer_file
export share
export cd_hds_base_url

echo "Uploading patients to cd-hds"
{ echo "hospitalInformation";
  echo "practitionerInformation";
  curl -sf "${share}/download?files=authored.json" | jq -rc "to_entries | .[0:${1:-10}] | .[].key"
} | xargs -P8 -I{} bash -c  'transfer_file "{}"'

echo "Upload finished"
