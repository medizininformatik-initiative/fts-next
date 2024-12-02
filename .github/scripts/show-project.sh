#!/bin/bash
set -euo pipefail

if ! cd_base_url="http://$(docker compose port cd-agent 8080)"; then
  >&2 echo "Unable to find clinical domain agent"
  exit 2
fi
cd_projetcs="$(curl -v "${cd_base_url}/api/v2/projects/{$1}")"
echo
echo "CD Projects: ${cd_projetcs}"
echo

if ! rd_base_url="http://$(docker compose port rd-agent 8080)"; then
  >&2 echo "Unable to find research domain agent"
  exit 2
fi
rd_projetcs="$(curl -sf "${rd_base_url}/api/v2/projects/{$1}")"
echo "RD Projects: ${rd_projetcs}"
