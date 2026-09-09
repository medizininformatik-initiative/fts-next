.PHONY:	compile test build coverage mutation clinical-domain-agent trust-center-agent research-domain-agent all

AGENTS := $(wildcard *-agent)
all: build
	@for agent in $(AGENTS); do \
		docker build -t ghcr.io/medizininformatik-initiative/fts/$$agent:local $$agent; \
    done

compile:
	mvn ${MAVEN_ARGS} clean compile

test:
	mvn ${MAVEN_ARGS} clean verify -Dfts.retryTimeout=false

format:
	find -type f -name '*.java' | xargs google-java-format -i

build:
	mvn ${MAVEN_ARGS} clean install -DskipTests -DskipITs

coverage:
	mvn ${MAVEN_ARGS} jacoco:report-aggregate@report

# Reports land in <module>/target/pit-reports. List the survivors with
# .github/scripts/mutation-survived.sh. To run one module: MAVEN_ARGS="-pl util" make mutation
mutation:
	.github/scripts/mutation.sh

$(AGENTS):
	mvn ${MAVEN_ARGS} clean package -DskipTests --projects $@ --also-make
	docker build -t ghcr.io/medizininformatik-initiative/fts/$@:local $@
