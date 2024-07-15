install:
	mvn -B install -T1C -Dmockserver.logLevel=WARN

image: Dockerfile .dockerignore application.yaml target
	docker build -t research-domain-agent .

.PHONY: image install
