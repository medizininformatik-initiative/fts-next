#!/bin/bash
set -euo pipefail

# Builds the CI test-data asset from the speicherwolke shares. Upload the result as a
# release asset and update TEST_DATA_TAG and TEST_DATA_CHECKSUM in .github/test/Makefile.

base_url="https://speicherwolke.uni-leipzig.de/public.php/webdav"
out="$(readlink -f "${1:-fts-test-data.tar.gz}")"
size="${2:-100}"
shares=(MioAzTLMjzbPNyx 4p2JNfHa8mXmLSF)

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

function download_file() {
  curl -sSfL --retry 5 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
    -u "${share}:" "${base_url}/${1}" -o "${data_dir}/$(basename "${1}")"
}
export -f download_file
export base_url

for share in "${shares[@]}"; do
  data_dir="${work_dir}/${share}"
  mkdir -p "${data_dir}"
  export share data_dir

  echo "Downloading ${size} patients of share ${share}"
  download_file authored.json
  jq "to_entries | .[0:${size}] | from_entries" "${data_dir}/authored.json" >"${work_dir}/authored.json"
  mv "${work_dir}/authored.json" "${data_dir}/authored.json"

  { echo "hospitalInformation";
    echo "practitionerInformation";
    jq -r 'keys_unsorted[]' "${data_dir}/authored.json"
  } | xargs -P8 -I{} bash -c 'download_file "kds/{}.json.gz"'

  download_file kds/checksums.sha256
  (cd "${data_dir}" \
    && sha256sum -c --ignore-missing --quiet checksums.sha256 \
    && sha256sum -- *.json.gz >checksums.sha256)
done

tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner \
  -C "${work_dir}" -cf - "${shares[@]}" | gzip -n >"${out}"
sha256sum "${out}"
