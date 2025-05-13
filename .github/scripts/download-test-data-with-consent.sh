#!/bin/bash
set -euo pipefail

share="https://speicherwolke.uni-leipzig.de/index.php/s/4p2JNfHa8mXmLSF"

mkdir -p ./test-data

function download_file() {
  curl -sf "${share}/download?path=kds&files=${1}.json.gz" -o "./test-data/${1}.json.gz"
}
export -f download_file
export share

echo "Downloading test data files"
{ echo "hospitalInformation";
  echo "practitionerInformation";
  curl -sf "${share}/download?files=authored.json" | jq -rc "to_entries | .[0:${1:-100}] | .[].key"
} | xargs -P8 -I{} bash -c  'download_file "{}"'

echo "Download finished"
