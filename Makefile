AGENTS := $(wildcard *-agent)

clean:
	mvn ${MAVEN_ARGS} clean

compile:
	mvn ${MAVEN_ARGS} compile

test:
	mvn ${MAVEN_ARGS} verify

build:
	mvn ${MAVEN_ARGS} package

coverage:
	mvn ${MAVEN_ARGS} jacoco:report-aggregate@report

images:
	@for agent in $(AGENTS); do \
    	$(MAKE) -C $$agent image; \
    done

e2e:
	$(MAKE) -C .github/test all

.PHONY:	clean test build images e2e
