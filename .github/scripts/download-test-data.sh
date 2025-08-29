#!/bin/bash
set -euo pipefail

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="${1}"

# Create directory based on share ID to avoid file collisions
data_dir="./test-data/${share}"
mkdir -p "${data_dir}"

function download_file() {
  curl -sSfL --retry 3 -u "${share}:" "${base_url}/kds/${1}.json.gz" -o "${data_dir}/${1}.json.gz"
}
export -f download_file
export base_url
export share
export data_dir

echo "Downloading test data files"
{ echo "hospitalInformation";
  echo "practitionerInformation";
  curl -sSLf --retry 3 -u "${share}:" "${base_url}/authored.json" | jq -rc "to_entries | .[0:${2:-100}] | .[].key"
} | xargs -P8 -I{} bash -c  'download_file "{}"'

curl -sSLf --retry 3 -u "${share}:" "${base_url}/kds/checksums.sha256" >"${data_dir}/checksums.sha256"
(cd "${data_dir}" && sha256sum -c checksums.sha256 --ignore-missing)

echo "Download finished"
