#!/bin/bash
# Local counterpart of the life-research/fhir-test-data action used in CI.
# Uses the same commit as build.yml, so local and CI data match.
# Requires oras: https://oras.land/docs/installation
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
workflow="${SCRIPT_DIR}/../workflows/build.yml"

set_name="${1}"
patients="${2:-}"

ref="$(grep -oE 'life-research/fhir-test-data@[0-9a-f]{40}' "${workflow}" | cut -d@ -f2 | sort -u)"
if [ "$(grep -c . <<<"${ref}")" -ne 1 ]; then
  >&2 echo "Expected exactly one life-research/fhir-test-data ref in ${workflow}, got: ${ref}"
  exit 1
fi

tool_dir="./test-data/.fhir-test-data-${ref}"
if [ ! -d "${tool_dir}" ]; then
  mkdir -p "${tool_dir}"
  curl -sSfL "https://github.com/life-research/fhir-test-data/archive/${ref}.tar.gz" \
    | tar -xz --strip-components=1 -C "${tool_dir}"
fi

rm -rf "./test-data/${set_name}"
"${tool_dir}/scripts/pull.sh" "${set_name}" "./test-data/${set_name}" "${patients}"
