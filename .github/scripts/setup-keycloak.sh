#!/bin/bash
set -euo pipefail

TOKEN=$(curl -v http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token')
echo "token: ${TOKEN}"

# create realm
curl -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "realm": "fts",
       "enabled": true,
       "displayName": "FTS",
       "displayNameHtml": "<div class=\"kc-logo-text\">FTS</div>",
       "sslRequired": "external",
       "registrationAllowed": false,
       "loginWithEmailAllowed": true,
       "duplicateEmailsAllowed": false,
       "resetPasswordAllowed": true,
       "editUsernameAllowed": false,
       "bruteForceProtected": true
     }' \
     "http://localhost:8080/admin/realms"


REALMS=$(curl  -H "Authorization: Bearer ${TOKEN}" http://localhost:8080/admin/realms)

# create client
echo "Create client fts-client"
curl -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "clientId": "fts-client",
       "name": "FTS Client",
       "enabled": true,
       "clientAuthenticatorType": "client-secret",
       "protocol": "openid-connect",
       "standardFlowEnabled": true,
       "serviceAccountsEnabled": true,
       "publicClient": false,
       "redirectUris": [
         "http://localhost:8080/*"
       ],
       "webOrigins": [
         "http://localhost:8080"
       ],
       "attributes": {
         "access.token.lifespan": "1800"
       }
     }' \
     "http://localhost:8080/admin/realms/fts/clients"




CLIENT_ID=$(curl -sf -H "Authorization: Bearer ${TOKEN}" http://localhost:8080/admin/realms/fts/clients | jq -r '.[] | select(.clientId == "fts-client") | .id')
echo "id of client with client_id FTSnext: ${CLIENT_ID}"

# add & assign role
curl -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "client",
       "description": "Role for FTS operations",
       "composite": false,
       "clientRole": true
     }' \
     "http://localhost:8080/admin/realms/fts/clients/${CLIENT_ID}/roles"

ROLE_ID=$(curl  -H "Authorization: Bearer ${TOKEN}" http://localhost:8080/admin/realms/fts/clients/${CLIENT_ID}/roles | jq -r '.[0].id')
echo "Role ID: ${ROLE_ID}"

SERVICE_ACCOUNT_USER_ID=$(curl -H "Authorization: Bearer ${TOKEN}" \
     "http://localhost:8080/admin/realms/fts/clients/${CLIENT_ID}/service-account-user" | jq -r '.id')
echo "SERVICE_ACCOUNT_USER_ID: ${SERVICE_ACCOUNT_USER_ID}"
echo "CLIENT_ID: ${CLIENT_ID}"

curl -H "Authorization: Bearer ${TOKEN}" http://localhost:8080/admin/realms/fts/users/${SERVICE_ACCOUNT_USER_ID}/role-mappings

p="{
              \"id\": \"${ROLE_ID}\",
              \"description\": \"Role for FTS operations\",
              \"composite\": false,
              \"clientRole\": true
            }"
echo
echo "data: ${p}"

curl -v -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d "[{
            \"id\": \"${ROLE_ID}\",
            \"name\": \"client\",
            \"description\": \"Role for FTS operations\",
            \"composite\": false,
            \"clientRole\": true,
            \"containerId\": \"${CLIENT_ID}\"
          }]" \
     "http://localhost:8080/admin/realms/fts/users/${SERVICE_ACCOUNT_USER_ID}/role-mappings/clients/${CLIENT_ID}"


SECRET=$(curl -X POST -H "Authorization: Bearer ${TOKEN}" http://localhost:8080/admin/realms/fts/clients/${CLIENT_ID}/client-secret | jq -r '.value')
sed -i "s/            client-secret: .*/            client-secret: ${SECRET}/" "../../clinical-domain-agent/application-auth:oauth2.yaml"
