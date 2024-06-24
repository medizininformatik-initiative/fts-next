#!/usr/bin/env sh

cd_agent_base_url="http://$(docker compose port cd-agent 8080)"

curl -v -X POST "${cd_agent_base_url}/api/v2/process/${1}/start"
