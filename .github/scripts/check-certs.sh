#!/bin/bash
set -euo pipefail

# Exit 0 if all expected test certs exist and are valid for at least
# CERT_TTL_SECONDS into the future. Exit 1 otherwise (cert missing or
# expiring within the window).
#
# Usage: check-certs.sh <ssl-dir> [ttl-seconds]

SSL_DIR="${1:?usage: check-certs.sh <ssl-dir> [ttl-seconds]}"
TTL="${2:-${CERT_TTL_SECONDS:-86400}}"

CERTS=(
  ca.crt
  server.crt
  server-rd-agent.crt
  client-tca.crt
  client-cd-agent.crt
  client-rd-agent.crt
  client-no-ca.crt
)

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
