#!/bin/bash

# Create CA
echo "Creating CA..."
openssl genpkey -quiet -algorithm RSA -out "ca.key" -pkeyopt rsa_keygen_bits:2048 >/dev/null
openssl req -x509 -new -key "ca.key" -days 60 -out "ca.crt" -subj "/CN=fts.smith.care"

# Create server certificate
echo "Creating server certificate..."
openssl genpkey -quiet -algorithm RSA -out "server.key" -pkeyopt rsa_keygen_bits:2048 >/dev/null
openssl req -new -key "server.key" -subj "/CN=${1:-server.example.com}" \
  | openssl x509 -req -CA "ca.crt" -CAkey "ca.key" -CAcreateserial -out "server.crt" -days 30

for CLIENT_CN in "${@:2}"; do
  echo "Creating '$CLIENT_CN' client certificate for CN=$CLIENT_CN..."
  openssl genpkey -quiet -algorithm RSA -out "client-${CLIENT_CN}.key" -pkeyopt rsa_keygen_bits:2048 >/dev/null
  openssl req -new -key "client-${CLIENT_CN}.key" -subj "/CN=${CLIENT_CN}" \
  | openssl x509 -req -CA "ca.crt" -CAkey "ca.key" -CAcreateserial -out "client-${CLIENT_CN}.crt" -days 30
done
