#!/usr/bin/env sh

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

if [ -z "${1}" ]; then
  >&2 echo "PATIENT_ID must be passed as first argument"
  exit 2
fi

if ! cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"; then
  >&2 echo "Unable to find clinical domain health data store URL"
  exit 2
fi

PATIENT_ID="${1}" \
IDENTIFIER_SYSTEM="http://fts.smith.care" \
YEAR="$(date +"%Y")" \
envsubst '$PATIENT_ID $IDENTIFIER_SYSTEM $YEAR' \
  <"${SCRIPT_DIR}/patient.tmpl.json" \
  | curl -v --data-binary @- -H "Content-Type: application/fhir+json" "${cd_hds_base_url}"
