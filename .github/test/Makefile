.PHONY: all pull generate-certs check-certs start upload-consents check-consent upload-test-data \
	transfer-all transfer-list transfer-with-fhir-consent-list wait  \
	check-status check-resources check-pseudonymization show-status show-hds clean-dbs clean-rd-hds \
	download-test-data download-test-data-checksums check-test-data \
	openapi-specs list-projects show-project

all: pull generate-certs start upload-test-data transfer-all wait check-status check-resources check-pseudonymization show-hds clean-dbs

COMPOSE_FILES?=-f compose.yaml
SERVICES?=
CERT_TTL_SECONDS?=86400

SERVERS?=--server tc-agent --server rd-agent
CLIENTS?=--client cd-agent --client rd-agent

SCRIPTS:=../scripts
GEN_CERTS:=../../util/src/main/bash/generate-certificates.sh

pull:
	docker compose ${COMPOSE_FILES} pull -q --ignore-pull-failures ${SERVICES}

generate-certs:
	$(GEN_CERTS) ./ssl ${SERVERS} ${CLIENTS}

check-certs:
	@$(SCRIPTS)/check-certs.sh ./ssl $(CERT_TTL_SECONDS) ${SERVERS} ${CLIENTS} || $(MAKE) generate-certs

start: check-certs
	docker compose ${COMPOSE_FILES} up --wait --pull missing ${SERVICES}

download-test-data-checksums:
	$(SCRIPTS)/download-test-data-checksums.sh MioAzTLMjzbPNyx
	$(SCRIPTS)/download-test-data-checksums.sh 4p2JNfHa8mXmLSF

download-test-data:
	$(SCRIPTS)/download-test-data.sh MioAzTLMjzbPNyx ${TEST_SET_SIZE}
	$(SCRIPTS)/download-test-data.sh 4p2JNfHa8mXmLSF ${TEST_SET_SIZE}

check-test-data:
	(cd ./test-data/MioAzTLMjzbPNyx && sha256sum -c checksums.sha256 --ignore-missing)
	(cd ./test-data/4p2JNfHa8mXmLSF && sha256sum -c checksums.sha256 --ignore-missing)

upload-consents:
	$(SCRIPTS)/upload-consents.sh ${TEST_SET_SIZE}

upload-test-data:
	$(SCRIPTS)/upload-data.sh

transfer-all:
	$(SCRIPTS)/start-transfer-all.sh ${PROJECT} >process.url

transfer-list:
	$(SCRIPTS)/start-transfer-list.sh ${PROJECT} MioAzTLMjzbPNyx ${TEST_SET_SIZE} >process.url

transfer-with-fhir-consent-list:
	$(SCRIPTS)/start-transfer-list.sh ${PROJECT} 4p2JNfHa8mXmLSF ${TEST_SET_SIZE} >process.url

wait:
	$(SCRIPTS)/await-transfer.sh "$(shell cat process.url)"

check-consent:
	$(SCRIPTS)/check-consent.sh ${TEST_SET_SIZE}

check-status:
	$(SCRIPTS)/check-status.sh "$(shell cat process.url)" ${RESULTS_FILE}

check-resources:
	$(SCRIPTS)/check-resources.sh ${RESULTS_FILE}

check-pseudonymization:
	$(SCRIPTS)/check-pseudonymization.sh

show-status:
	curl -sSf "$(shell cat process.url)"

list-projects:
	$(SCRIPTS)/list-projects.sh

show-project:
	$(SCRIPTS)/show-project.sh

show-hds:
	$(SCRIPTS)/show-hds.sh

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
	$(SCRIPTS)/openapi-specs.sh ${AGENT}
