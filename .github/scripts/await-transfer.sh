#!/bin/bash
set -euo pipefail

if [ -z "${1}" ]; then
  >&2 echo "PROCESS_URL must be passed as first argument"
  exit 2
fi

function printStatus() {
  phase="$(echo "${1}" | jq -r '.phase')"
  sent="$(echo "${1}" | jq -r '.bundlesSentCount')"
  skipped="$(echo "${1}" | jq -r '.patientsSkippedCount')"
  printf "· %-14s transferred: %-5d skipped: %-5d\n" "${phase}" "${sent}" "${skipped}"
}

echo "Wait for transfer process to finish..."
while response="$(curl -sf "${1}")" && [ "$(echo "${response}" | jq -r '.phase')" == "RUNNING" ]; do
  printStatus "${response}"
  sleep 5
done

printStatus "${response}"

if [ "$(echo "${response}" | jq -r '.phase')" == "ERROR" ]; then
  exit 1
fi
