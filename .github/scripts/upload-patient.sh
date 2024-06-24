#!/usr/bin/env sh

cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"

PATIENT_ID="${1}" \
IDENTIFIER_SYSTEM="http://fts.smith.care" \
YEAR="$(date +"%Y")" \
envsubst '$PATIENT_ID $IDENTIFIER_SYSTEM $YEAR' \
  <patient.tmpl.json \
  | curl -v --data-binary @- -H "Content-Type: application/fhir+json" "${cd_hds_base_url}"
