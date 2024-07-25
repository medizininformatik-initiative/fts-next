AGENTS := $(wildcard *-agent)

clean:
	mvn ${MAVEN_ARGS} clean

compile:
	mvn ${MAVEN_ARGS} compile

test:
	mvn ${MAVEN_ARGS} verify

build:
	mvn ${MAVEN_ARGS} package

build/utils:
	mvn ${MAVEN_ARGS} install --projects .,api,util,test-util,monitoring-util

coverage:
	mvn ${MAVEN_ARGS} jacoco:report-aggregate@report

images:
	@for agent in $(AGENTS); do \
    	$(MAKE) -C $$agent image; \
    done

e2e:
	$(MAKE) -C .github/test all

.PHONY:	clean test build build/utils images e2e
