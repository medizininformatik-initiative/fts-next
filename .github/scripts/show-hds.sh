#!/bin/bash
set -euo pipefail

if ! cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"; then
  >&2 echo "Unable to find clinical domain health data store URL"
  exit 2
fi

if ! rd_hds_base_url="http://$(docker compose port rd-hds 8080)/fhir"; then
  >&2 echo "Unable to find research domain health data store URL"
  exit 2
fi

blazectl count-resources --server "${cd_hds_base_url}"
blazectl count-resources --server "${rd_hds_base_url}"
