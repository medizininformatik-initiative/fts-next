#!/bin/bash
set -euo pipefail

if ! cd_base_url="http://$(docker compose port cd-agent 8080)"; then
  >&2 echo "Unable to find clinical domain agent"
  exit 2
fi

cd_projects="$(curl -sSf "${cd_base_url}/api/v2/projects")"

echo
echo "CD Projects: ${cd_projects}"
echo

if ! rd_base_url="http://$(docker compose port rd-agent 8080)"; then
  >&2 echo "Unable to find research domain agent"
  exit 2
fi
rd_projetcs="$(curl -sSf "${rd_base_url}/api/v2/projects")"
echo "RD Projects: ${rd_projetcs}"
