#!/bin/bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  >&2 echo "FILENAME must be passed as first argument"
  exit 2
fi

grep -o -e "https://[^ \"]*" <"${1}" | while read -r url; do
  if curl -fsL --head "$url" >/dev/null; then
    echo "URL is available: $url"
  else
    echo "URL is unavailable: $url"
    exit 1
  fi
done
