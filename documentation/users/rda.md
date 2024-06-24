# FTS Research Domain Agent

## Using Docker (Recommended)

*For prerequisites, please see the docker [section](README.md#running-with-docker-recommended) of
the common usage
instructions*

* Download the latest release [artifacts][rda/docker]
* Unpack the file contents in a directory of your choice
* Configure the agent by customizing provided `application.yaml` (
  see [Configuration](#configuration))
* Run `docker compose up` from that directory

## Using Java

*For prerequisites, please see the java [section](README.md#running-with-java) of the common usage
instructions*

* Download the latest release [artifacts][rda/java]
* Unpack the file contents in a directory of your choice
* Configure the agent by customizing provided `application.yaml` (
  see [Configuration](#configuration))
* Run `java -jar research-domain-agent-*.jar` from that directory

## Configuration

The provided `application.yaml` contains hints on what can be configured. Further configuration
instructions will be
added to this document in a later release.

## Monitoring

Prometheus metrics can be found at `http://localhost:9090/actuator/prometheus`

[rda/java]: https://git.smith.care/api/v4/projects/135/packages/generic/research-domain-agent/4.2.0/research-domain-agent-java.zip

[rda/docker]: https://git.smith.care/api/v4/projects/135/packages/generic/research-domain-agent/4.2.0/research-domain-agent-docker.zip
