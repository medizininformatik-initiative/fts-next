#!/bin/bash
set -euo pipefail

# Exit 0 if all expected test certs exist and are valid for at least
# CERT_TTL_SECONDS into the future. Exit 1 otherwise (cert missing or
# expiring within the window).
#
# Usage: check-certs.sh <ssl-dir> <ttl-seconds> --server <CN> [--server <CN> ...] --client <CN> [--client <CN> ...]

SSL_DIR="${1:?usage: check-certs.sh <ssl-dir> <ttl-seconds> --server <CN> ... --client <CN> ...}"
shift
TTL="${1:?usage: check-certs.sh <ssl-dir> <ttl-seconds> --server <CN> ... --client <CN> ...}"
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

# Build the list of expected cert files
CERTS=("ca.crt")
for cn in "${SERVERS[@]}"; do
  CERTS+=("server-${cn}.crt")
done
for cn in "${CLIENTS[@]}"; do
  CERTS+=("client-${cn}.crt")
done
CERTS+=("client-no-ca.crt")

for name in "${CERTS[@]}"; do
  path="$SSL_DIR/$name"
  if [ ! -f "$path" ]; then
    echo "Cert $path missing"
    exit 1
  fi
  if ! openssl x509 -checkend "$TTL" -noout -in "$path" >/dev/null 2>&1; then
    echo "Cert $path expiring within ${TTL}s"
    exit 1
  fi
done
