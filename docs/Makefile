install:
	npm install

build: install
	npm run docs:build

generate-openapi:
	docker compose --project-name fts-docs -f ../.github/test/compose.yaml up --wait cd-agent tc-agent rd-agent keycloak keystore --no-deps
	../.github/scripts/openapi-specs.sh cd-agent --project-name fts-docs -f ../.github/test/compose.yaml >"public/cd-agent-openapi.json"
	../.github/scripts/openapi-specs.sh tc-agent --project-name fts-docs -f ../.github/test/compose.yaml >"public/tc-agent-openapi.json"
	../.github/scripts/openapi-specs.sh rd-agent --project-name fts-docs -f ../.github/test/compose.yaml >"public/rd-agent-openapi.json"
	docker compose --project-name fts-docs -f ../.github/test/compose.yaml down

.PHONY: install build
