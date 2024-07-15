install:
	mvn -B install -T1C -Dmockserver.logLevel=WARN

image: Dockerfile .dockerignore application.yaml target
	docker build -t trust-center-agent .

.PHONY: image install
