#!/bin/bash
set -euo pipefail

# Generate a CA and a set of server/client certificates signed by it.
#
# Usage: generate-certificates.sh <dir> --server <CN> [--server <CN> ...] --client <CN> [--client <CN> ...]
#
# The special client "no-ca" is always added (self-signed, not CA-signed).
#
# Output files in <dir>:
#   ca.key / ca.crt
#   server-<CN>.key / server-<CN>.crt   (one per --server)
#   client-<CN>.key / client-<CN>.crt   (one per --client)
#   client-no-ca.key / client-no-ca.crt

DIR="${1:?usage: generate-certificates.sh <dir> --server <CN> ... --client <CN> ...}"
shift

SERVERS=()
CLIENTS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --server)
      SERVERS+=("$2")
      shift 2
      ;;
    --client)
      CLIENTS+=("$2")
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ${#SERVERS[@]} -eq 0 ]]; then
  echo "Error: at least one --server is required" >&2
  exit 1
fi

mkdir -p "$DIR"

# Create CA (once)
echo "Creating CA..."
openssl genpkey -quiet -algorithm Ed25519 -out "${DIR}/ca.key" >/dev/null
openssl req -x509 -new -key "${DIR}/ca.key" -days 60 -out "${DIR}/ca.crt" -subj "/CN=fts.smith.care"

# Create server certificate(s)
for SERVER_CN in "${SERVERS[@]}"; do
  echo "Creating server certificate for CN=$SERVER_CN..."
  openssl genpkey -quiet -algorithm Ed25519 -out "${DIR}/server-${SERVER_CN}.key" >/dev/null
  openssl req -new -key "${DIR}/server-${SERVER_CN}.key" -subj "/CN=${SERVER_CN}/OU=Server/O=FTSnext" \
    | openssl x509 -req -CA "${DIR}/ca.crt" -CAkey "${DIR}/ca.key" -CAcreateserial -out "${DIR}/server-${SERVER_CN}.crt" -days 30
done

# Create client certificate(s)
for CLIENT_CN in "${CLIENTS[@]}"; do
  echo "Creating client certificate for CN=$CLIENT_CN..."
  openssl genpkey -quiet -algorithm Ed25519 -out "${DIR}/client-${CLIENT_CN}.key" >/dev/null
  openssl req -new -key "${DIR}/client-${CLIENT_CN}.key" -subj "/CN=${CLIENT_CN}/OU=Client/O=FTSnext" \
    | openssl x509 -req -CA "${DIR}/ca.crt" -CAkey "${DIR}/ca.key" -CAcreateserial -out "${DIR}/client-${CLIENT_CN}.crt" -days 30
done

# Always create 'no-ca' client certificate (self-signed)
echo "Creating 'no-ca' client certificate for CN=no-ca..."
openssl genpkey -quiet -algorithm Ed25519 -out "${DIR}/client-no-ca.key" >/dev/null
openssl req -new -x509 -key "${DIR}/client-no-ca.key" -subj "/CN=no-ca" -out "${DIR}/client-no-ca.crt" -days 30

chmod +r "${DIR}"/*.key
