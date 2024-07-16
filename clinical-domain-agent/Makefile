test/unit:
	mvn ${MAVEN_ARGS} test

test/integration:
	mvn ${MAVEN_ARGS} verify -DskipUnitTests

build: test/unit test/integration
	mvn ${MAVEN_ARGS} -DskipTests package

image: Dockerfile .dockerignore application.yaml target
	docker build -t clinical-domain-agent .

.PHONY: image build test/unit test/integration
