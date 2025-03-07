.PHONY: all pull start upload transfer wait check-status check-resources show-status show-hds clean-dbs clean-rd-hds

all: pull start upload transfer wait check-status check-resources show-hds clean-dbs

pull:
	docker compose pull -q --ignore-pull-failures

start:
	docker compose up --wait --pull missing

download:
	../scripts/download-test-data.sh ${TEST_SET_SIZE}

upload-consents:
	../scripts/upload-consents.sh ${TEST_SET_SIZE}

upload:
	../scripts/upload-data.sh ${TEST_SET_SIZE}

transfer-all:
	../scripts/start-transfer-all.sh ${PROJECT} >process.url

transfer-list:
	../scripts/start-transfer-list.sh ${PROJECT} ${TEST_SET_SIZE} >process.url

wait:
	../scripts/await-transfer.sh "$(shell cat process.url)"

check-consent:
	../scripts/check-consent.sh ${TEST_SET_SIZE}

check-status:
	../scripts/check-status.sh "$(shell cat process.url)" ${RESULTS_FILE}

check-resources:
	../scripts/check-resources.sh ${TEST_SET_SIZE}

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
