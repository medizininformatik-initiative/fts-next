.PHONY: all pull start upload transfer wait check-status check-resources show-status show-hds clean-dbs clean-rd-hds

all: pull generate-certs start upload transfer-all wait check-status check-resources show-hds clean-dbs

pull:
	docker compose pull -q --ignore-pull-failures

generate-certs:
	mkdir -p ./ssl
	(cd ./ssl && ../../../util/src/main/bash/generate-certificates.sh tc-agent tca && chmod +r *.key)

start:
	docker compose up --wait --pull missing

download:
	../scripts/download-test-data.sh MioAzTLMjzbPNyx ${TEST_SET_SIZE}

download-checksums:
	../scripts/download-test-data-checksums.sh MioAzTLMjzbPNyx

download-with-fhir-consent:
	../scripts/download-test-data.sh 4p2JNfHa8mXmLSF ${TEST_SET_SIZE}

download-with-fhir-consent-checksums:
	../scripts/download-test-data-checksums.sh 4p2JNfHa8mXmLSF

upload-consents:
	../scripts/upload-consents.sh ${TEST_SET_SIZE}

upload:
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

show-status:
	curl -s "$(shell cat process.url)"

list-projects:
	../scripts/list-projects.sh

show-project:
	../scripts/show-project.sh

show-hds:
	../scripts/show-hds.sh

clean-dbs:
	docker compose down cd-hds rd-hds gics-db gpas-db
	docker compose up --wait cd-hds rd-hds gics-db gpas-db

clean-rd-hds-and-gpas-db:
	docker compose down rd-hds gpas-db
	docker compose up --wait rd-hds gpas-db

openapi-specs:
	../scripts/openapi-specs.sh ${AGENT}
