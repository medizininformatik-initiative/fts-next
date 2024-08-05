pull:
	docker compose pull -q --ignore-pull-failures

start:
	docker compose up --wait

upload:
	../scripts/upload-consent.sh patient-1
	../scripts/upload-patient.sh patient-1

transfer:
	../scripts/start-transfer.sh example >process.url

wait:
	../scripts/await-transfer.sh "$(shell cat process.url)"

check-status:
	../scripts/check-status.sh "$(shell cat process.url)"

check-resources:
	../scripts/check-resources.sh

show-status:
	curl -sf "$(shell cat process.url)"

clean-dbs:
	docker compose down cd-hds rd-hds gics-db
	docker compose up --wait cd-hds rd-hds gics-db

clean-rd-hds:
	docker compose down rd-hds
	docker compose up --wait rd-hds

all: pull start upload transfer wait check-status check-resources clean-dbs

.PHONY: all pull start upload transfer wait check-status check-resources
