#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

if [ -z "${1:-}" ]; then
  >&2 echo "PATIENT_ID must be passed as first argument"
  exit 2
fi

if ! gics_base_url="http://$(docker compose port gics 8080)/ttp-fhir/fhir/gics"; then
  >&2 echo "Unable to find gics URL"
  exit 2
fi

PATIENT_ID="${1}" \
QUESTIONNAIRE_RESPONSE_UUID="$(uuidgen)" \
RESEARCH_STUDY_UUID="$(uuidgen)" \
AUTHORED="${2:-"2024-05-01T00:00:00+02:00"}" \
envsubst "\$PATIENT_ID \$QUESTIONNAIRE_RESPONSE_UUID \$RESEARCH_STUDY_UUID \$AUTHORED" \
  <"${SCRIPT_DIR}/consent.tmpl.json" \
  | curl -sf --data-binary @- -H "Content-Type: application/fhir+json" "${gics_base_url}/\$addConsent" \
  >/dev/null
