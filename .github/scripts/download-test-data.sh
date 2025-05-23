#!/bin/bash
set -euo pipefail

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
share="${1}"

mkdir -p ./test-data

function download_file() {
  curl -sLf -u "${share}:" "${base_url}/kds/${1}.json.gz" -o "./test-data/${1}.json.gz"
}
export -f download_file
export base_url
export share

echo "Downloading test data files"
{ echo "hospitalInformation";
  echo "practitionerInformation";
  curl -sLf -u "${share}:" "${base_url}/authored.json" | jq -rc "to_entries | .[0:${2:-100}] | .[].key"
} | xargs -P8 -I{} bash -c  'download_file "{}"'

curl -sLf -u "${share}:" "${base_url}/kds/checksums.sha256" >./test-data/checksums.sha256
(cd ./test-data && sha256sum -c checksums.sha256 --ignore-missing)

echo "Download finished"
