#!/bin/bash
set -euo pipefail

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="${1}"

# Create directory based on share ID to avoid file collisions
data_dir="./test-data/${share}"
mkdir -p "${data_dir}"
curl -sSfL --retry 5 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
  -u "${share}:" "${base_url}/kds/checksums.sha256" -o "${data_dir}/checksums.sha256"
