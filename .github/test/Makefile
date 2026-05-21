.PHONY: all pull generate-certs check-certs start upload-consents check-consent upload-test-data \
	transfer-all transfer-list transfer-with-fhir-consent-list wait  \
	check-status check-resources check-pseudonymization show-status show-hds clean-dbs clean-rd-hds \
	download-test-data download-test-data-checksums check-test-data \
	openapi-specs list-projects show-project

all: pull generate-certs start upload-test-data transfer-all wait check-status check-resources check-pseudonymization show-hds clean-dbs

COMPOSE_FILES?=-f compose.yaml
SERVICES?=
CERT_TTL_SECONDS?=86400

pull:
	docker compose ${COMPOSE_FILES} pull -q --ignore-pull-failures ${SERVICES}

generate-certs:
	mkdir -p ./ssl
	(cd ./ssl && ../../../util/src/main/bash/generate-certificates.sh tc-agent tca cd-agent rd-agent && chmod +r *.key)
	(cd ./ssl && \
	  openssl genpkey -quiet -algorithm Ed25519 -out server-rd-agent.key && \
	  openssl req -new -key server-rd-agent.key -subj "/CN=rd-agent/OU=Server/O=FTSnext" \
	  | openssl x509 -req -CA ca.crt -CAkey ca.key -CAcreateserial -out server-rd-agent.crt -days 30 && \
	  chmod +r server-rd-agent.key)

check-certs:
	@../scripts/check-certs.sh ./ssl $(CERT_TTL_SECONDS) || $(MAKE) generate-certs

start: check-certs
	docker compose ${COMPOSE_FILES} up --wait --pull missing ${SERVICES}

download-test-data-checksums:
	../scripts/download-test-data-checksums.sh MioAzTLMjzbPNyx
	../scripts/download-test-data-checksums.sh 4p2JNfHa8mXmLSF

download-test-data:
	../scripts/download-test-data.sh MioAzTLMjzbPNyx ${TEST_SET_SIZE}
	../scripts/download-test-data.sh 4p2JNfHa8mXmLSF ${TEST_SET_SIZE}

check-test-data:
	(cd ./test-data/MioAzTLMjzbPNyx && sha256sum -c checksums.sha256 --ignore-missing)
	(cd ./test-data/4p2JNfHa8mXmLSF && sha256sum -c checksums.sha256 --ignore-missing)

upload-consents:
	../scripts/upload-consents.sh ${TEST_SET_SIZE}

upload-test-data:
	../scripts/upload-data.sh

transfer-all:
	../scripts/start-transfer-all.sh ${PROJECT} >process.url

transfer-list:
	../scripts/start-transfer-list.sh ${PROJECT} MioAzTLMjzbPNyx ${TEST_SET_SIZE} >process.url

transfer-with-fhir-consent-list:
	../scripts/start-transfer-list.sh ${PROJECT} 4p2JNfHa8mXmLSF ${TEST_SET_SIZE} >process.url

wait:
	../scripts/await-transfer.sh "$(shell cat process.url)"

check-consent:
	../scripts/check-consent.sh ${TEST_SET_SIZE}

check-status:
	../scripts/check-status.sh "$(shell cat process.url)" ${RESULTS_FILE}

check-resources:
	../scripts/check-resources.sh ${RESULTS_FILE}

check-pseudonymization:
	../scripts/check-pseudonymization.sh

show-status:
	curl -sSf "$(shell cat process.url)"

list-projects:
	../scripts/list-projects.sh

show-project:
	../scripts/show-project.sh

show-hds:
	../scripts/show-hds.sh

clean-dbs:
	docker compose ${COMPOSE_FILES} down cd-hds rd-hds gics-db gpas-db
	docker compose ${COMPOSE_FILES} up --wait cd-hds rd-hds gics-db gpas-db

clean-rd-hds:
	docker compose ${COMPOSE_FILES} down rd-hds
	docker compose ${COMPOSE_FILES} up --wait rd-hds

clean-rd-hds-and-gpas-db:
	docker compose ${COMPOSE_FILES} down rd-hds gpas-db
	docker compose ${COMPOSE_FILES} up --wait rd-hds gpas-db

openapi-specs:
	../scripts/openapi-specs.sh ${AGENT}
