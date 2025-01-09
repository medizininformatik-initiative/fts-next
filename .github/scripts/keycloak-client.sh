#!/bin/bash
set -euo pipefail

token=$(curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token')
echo "token: ${token}"

client_id=$(curl -sf -H "Authorization: Bearer ${token}" http://localhost:8080/admin/realms/fts/clients | jq -r '.[] | select(.clientId == "FTSnext") | .id')
echo "id of client with client_id FTSnext: ${client_id}"

secret=$(curl -X POST -H "Authorization: Bearer ${token}" http://localhost:8080/admin/realms/fts/clients/${client_id}/client-secret | jq '.value')

echo "secret: ${secret}"
