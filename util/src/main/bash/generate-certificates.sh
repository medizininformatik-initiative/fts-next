#!/bin/bash

# Create CA
echo "Creating CA..."
openssl genpkey -quiet -algorithm Ed25519 -out "ca.key" >/dev/null
openssl req -x509 -new -key "ca.key" -days 60 -out "ca.crt" -subj "/CN=fts.smith.care"

# Create server certificate
echo "Creating server certificate..."
openssl genpkey -quiet -algorithm Ed25519 -out "server.key" >/dev/null
openssl req -new -key "server.key" -subj "/CN=${1:-server.example.com}" \
  | openssl x509 -req -CA "ca.crt" -CAkey "ca.key" -CAcreateserial -out "server.crt" -days 30

for CLIENT_CN in "${@:2}"; do
  echo "Creating '$CLIENT_CN' client certificate for CN=$CLIENT_CN..."
  openssl genpkey -quiet -algorithm Ed25519 -out "client-${CLIENT_CN}.key" >/dev/null
  openssl req -new -key "client-${CLIENT_CN}.key" -subj "/CN=${CLIENT_CN}" \
    | openssl x509 -req -CA "ca.crt" -CAkey "ca.key" -CAcreateserial -out "client-${CLIENT_CN}.crt" -days 30
done

echo "Creating 'no-ca' client certificate for CN=no-ca..."
openssl genpkey -quiet -algorithm Ed25519 -out "client-no-ca.key" >/dev/null
openssl req -new -x509 -key "client-no-ca.key" -subj "/CN=no-ca" -out "client-no-ca.crt" -days 30
