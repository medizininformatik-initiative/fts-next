#!/bin/bash
set -euo pipefail

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="${1}"

# Create directory based on share ID to avoid file collisions
data_dir="./test-data/${share}"
mkdir -p "${data_dir}"
curl -sSfL --retry 3 -u "${share}:" "${base_url}/kds/checksums.sha256" >"${data_dir}/checksums.sha256"
