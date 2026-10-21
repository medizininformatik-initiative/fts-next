#!/bin/bash
set -euo pipefail

if [ -z "${1:-}" ] || [ -z "${2:-}" ]; then
  >&2 echo "Usage: $0 <tag> <sha256>"
  exit 2
fi

tag="${1}"
checksum="${2}"
base_url="${TEST_DATA_BASE_URL:-https://github.com/medizininformatik-initiative/fts-next/releases/download}"
asset="fts-test-data.tar.gz"

echo "Downloading test data ${tag}"
curl -sSfL --retry 5 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
  "${base_url}/${tag}/${asset}" -o "${asset}"

if ! echo "${checksum}  ${asset}" | sha256sum -c --quiet; then
  >&2 echo "ERROR: ${asset} of ${tag} does not match TEST_DATA_CHECKSUM ${checksum}"
  rm "${asset}"
  exit 1
fi

rm -rf test-data
mkdir test-data
tar -xzf "${asset}" -C test-data
rm "${asset}"

for data_dir in test-data/*/; do
  (cd "${data_dir}" && sha256sum -c --quiet checksums.sha256)
done

echo "Download finished"
