.PHONY:	compile test build coverage clinical-domain-agent trust-center-agent research-domain-agent all

AGENTS := $(wildcard *-agent)
all: build
	@for agent in $(AGENTS); do \
		docker build -t ghcr.io/medizininformatik-initiative/fts/$$agent:local $$agent; \
    done

compile:
	mvn ${MAVEN_ARGS} clean compile

test:
	mvn ${MAVEN_ARGS} clean verify

format:
	find -type f -name '*.java' | xargs google-java-format -i

build:
	mvn ${MAVEN_ARGS} clean install -DskipTests

coverage:
	mvn ${MAVEN_ARGS} jacoco:report-aggregate@report

$(AGENTS):
	mvn ${MAVEN_ARGS} clean package -DskipTests --projects $@ --also-make
	docker build -t ghcr.io/medizininformatik-initiative/fts/$@:local $@
