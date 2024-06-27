AGENTS := $(wildcard *-agent)

$(AGENTS):
	$(MAKE) -C $@ $(MAKECMDGOALS)

clean:
	mvn clean

build:
	mvn install -T1C -Dmockserver.logLevel=WARN

build-cda:
	mvn install -T1C -Dmockserver.logLevel=WARN --projects clinical-domain-agent

build-rda:
	mvn install -T1C -Dmockserver.logLevel=WARN --projects research-domain-agent

build-tca:
	mvn install -T1C -Dmockserver.logLevel=WARN --projects trust-center-agent

images:
	@for agent in $(AGENTS); do \
    	$(MAKE) -C $$agent image; \
    done

.PHONY:
	$(AGENTS) clean build image
