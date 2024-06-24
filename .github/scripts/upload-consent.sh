#!/usr/bin/env sh

gics_base_url="http://$(docker compose port gics 8080)/ttp-fhir/fhir/gics"

PATIENT_ID="${1}" \
QUESTIONNAIRE_RESPONSE_UUID="$(uuidgen)" \
RESEARCH_STUDY_UUID="$(uuidgen)" \
AUTHORED="$(date +"%Y-%m-%dT%H:%M:%S%:z")" \
envsubst '$PATIENT_ID $QUESTIONNAIRE_RESPONSE_UUID $RESEARCH_STUDY_UUID $AUTHORED' \
  <consent.tmpl.json \
  | curl -v --data-binary @- -H "Content-Type: application/fhir+json" "${gics_base_url}/\$addConsent"
