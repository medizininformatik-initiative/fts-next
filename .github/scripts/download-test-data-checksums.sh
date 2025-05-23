#!/bin/bash
set -euo pipefail

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="${1}"

mkdir -p ./test-data
curl -sLf -u "${share}:" "${base_url}/kds/checksums.sha256" >./test-data/checksums.sha256
