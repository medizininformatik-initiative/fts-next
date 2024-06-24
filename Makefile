AGENTS := $(wildcard *-agent)

$(AGENTS):
	$(MAKE) -C $@ $(MAKECMDGOALS)

clean:
	mvn clean

build:
	mvn install -Dmockserver.logLevel=WARN

images:
	@for agent in $(AGENTS); do \
    	$(MAKE) -C $$agent image; \
    done

.PHONY:
	$(AGENTS) clean build image
