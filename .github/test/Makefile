.PHONY: all pull start upload transfer wait check-status check-resources show-status show-hds clean-dbs clean-rd-hds

all: pull start upload transfer wait check-status check-resources show-hds clean-dbs

pull:
	docker compose pull -q --ignore-pull-failures

start:
	docker compose up --wait

upload:
	../scripts/upload-data.sh 100

transfer:
	../scripts/start-transfer.sh example >process.url

transfer-with-manual-cohort-selection:
	../scripts/start-transfer-manual-cohort-selection.sh example 100 >process.url

wait:
	../scripts/await-transfer.sh "$(shell cat process.url)"

check-consent:
	../scripts/check-consent.sh 100

check-status:
	../scripts/check-status.sh "$(shell cat process.url)" 100

check-resources:
	../scripts/check-resources.sh 100

show-status:
	curl -sf "$(shell cat process.url)"

show-hds:
	../scripts/show-hds.sh

clean-dbs:
	docker compose down cd-hds rd-hds gics-db gpas-db
	docker compose up --wait cd-hds rd-hds gics-db gpas-db

clean-rd-hds-and-gpas-db:
	docker compose down rd-hds gpas-db
	docker compose up --wait rd-hds gpas-db
