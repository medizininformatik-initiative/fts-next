AGENTS := $(wildcard *-agent)

$(AGENTS):
	$(MAKE) -C $@ $(MAKECMDGOALS)

clean:
	mvn ${MAVEN_ARGS} clean

build:
	mvn ${MAVEN_ARGS} compile -T1C

test:
	mvn ${MAVEN_ARGS} verify

install:
	mvn ${MAVEN_ARGS} install

install-dependencies:
	mvn ${MAVEN_ARGS} install --projects .,api,util,test-util,monitoring-util

images:
	@for agent in $(AGENTS); do \
    	$(MAKE) -C $$agent image; \
    done

.PHONY:
	$(AGENTS) clean build image
