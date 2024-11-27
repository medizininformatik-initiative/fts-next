AGENTS := $(wildcard *-agent)

compile:
	mvn ${MAVEN_ARGS} compile

test:
	mvn ${MAVEN_ARGS} verify

build:
	mvn ${MAVEN_ARGS} package -DskipTests

coverage:
	mvn ${MAVEN_ARGS} jacoco:report-aggregate@report

$(AGENTS):
	mvn ${MAVEN_ARGS} package -DskipTests --projects $@ --also-make
	docker build -t ghcr.io/medizininformatik-initiative/fts/$@:local $@

all: build
	mvn ${MAVEN_ARGS} package -DskipTests
	@for agent in $(AGENTS); do \
		docker build -t ghcr.io/medizininformatik-initiative/fts/$$agent:local $$agent; \
    done

.PHONY:	compile test build coverage $(AGENTS) all
