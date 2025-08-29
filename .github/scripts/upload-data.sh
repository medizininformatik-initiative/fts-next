#!/bin/bash
set -euo pipefail

if ! cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"; then
  >&2 echo "Unable to find clinical domain health data store URL"
  exit 2
fi

# Upload data from both datasets
blazectl upload ./test-data/MioAzTLMjzbPNyx --server "${cd_hds_base_url}" -c 8
blazectl upload ./test-data/4p2JNfHa8mXmLSF --server "${cd_hds_base_url}" -c 8
