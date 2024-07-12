#!/bin/bash
set -euo pipefail

if [ -z "${1}" ]; then
  >&2 echo "PROCESS_URL must be passed as first argument"
  exit 2
fi

echo -n "Wait for transfer process to finish..."
while [ "$(curl -sf "${1}" | jq -r '.phase')" != "COMPLETED" ]; do
  echo -n "."
  sleep 5
done
echo " Done"
