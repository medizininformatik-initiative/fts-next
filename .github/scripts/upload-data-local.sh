#!/bin/bash
# Upload transaction bundles from a local bundles.ndjson.gz into cd-hds.
#
# blazectl (>=1.4) still consumes a directory of per-bundle JSON files, so this
# script splits the gzipped ndjson into a temporary directory of one-bundle-
# per-file, then invokes `blazectl upload` against that dir. The temp dir is
# always cleaned up (trap on EXIT) — no cache reuse between runs.
#
# Usage:
#   upload-data-local.sh <bundles.ndjson.gz>
#
# Env knobs:
#   COMPOSE_PROJECT    docker compose project name (default: fts-test)
#   BLAZECTL           path to blazectl binary (default: blazectl from PATH)
#   UPLOAD_CONCURRENCY blazectl --concurrency (default: 8, same as upload-data.sh)
#   SPLIT_DIR          override split tmpdir parent (default: mktemp default)
set -euo pipefail

if [ -z "${1:-}" ]; then
  >&2 echo "Usage: $(basename "$0") <bundles.ndjson.gz>"
  exit 2
fi

src="${1}"
if [ ! -f "${src}" ]; then
  >&2 echo "ERROR: source file not found: ${src}"
  exit 2
fi

COMPOSE_PROJECT="${COMPOSE_PROJECT:-fts-test}"
BLAZECTL="${BLAZECTL:-blazectl}"
UPLOAD_CONCURRENCY="${UPLOAD_CONCURRENCY:-8}"

if ! command -v "${BLAZECTL}" >/dev/null 2>&1; then
  >&2 echo "ERROR: blazectl not found (BLAZECTL=${BLAZECTL})"
  exit 2
fi

if ! cd_hds_base_url="http://$(docker compose -p "${COMPOSE_PROJECT}" port cd-hds 8080)/fhir"; then
  >&2 echo "ERROR: unable to find cd-hds URL (compose project=${COMPOSE_PROJECT})"
  exit 2
fi

if [ -n "${SPLIT_DIR:-}" ]; then
  mkdir -p "${SPLIT_DIR}"
  tmpdir="$(mktemp -d -p "${SPLIT_DIR}" "upload-XXXXXX")"
else
  tmpdir="$(mktemp -d -t "upload-data-local-XXXXXX")"
fi
trap 'rm -rf "${tmpdir}"' EXIT INT TERM

echo "[upload-data-local] splitting ${src} -> ${tmpdir}"
# One bundle per file, zero-padded so blazectl iteration order matches input.
zcat "${src}" \
  | awk -v d="${tmpdir}" '
      { f = sprintf("%s/b-%010d.json", d, NR); print > f; close(f) }
      NR % 10000 == 0 { printf "[upload-data-local]  split %d bundles\n", NR > "/dev/stderr" }
    '

count="$(find "${tmpdir}" -maxdepth 1 -name 'b-*.json' | wc -l)"
echo "[upload-data-local] split complete: ${count} bundles"
if [ "${count}" -eq 0 ]; then
  >&2 echo "ERROR: no bundles extracted from ${src}"
  exit 1
fi

echo "[upload-data-local] uploading to ${cd_hds_base_url} (concurrency=${UPLOAD_CONCURRENCY})"
"${BLAZECTL}" upload "${tmpdir}" --server "${cd_hds_base_url}" -c "${UPLOAD_CONCURRENCY}"

echo "[upload-data-local] done (${count} bundles uploaded)"
