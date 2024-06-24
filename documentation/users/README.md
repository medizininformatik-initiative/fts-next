# FHIR Transfer Services User Guide

Welcome to the FHIR Transfer Services user guide! This guide will help you get started with running
the FHIR Transfer
Services components: Clinical Domain Transfer Agent (CDA), Research Domain Transfer Agent (RDA), and
Trust Center
Agent (TCA). As the three components are intended to be run in different "domains" of a data
integration center, running
and configuring each component will be described in respective documents.

## Running with Docker (recommended)

FTS docker images are distributed to the SMITH container registry (`creg.smith.care`). For executing
the different
components, docker compose files will be provided.

**Prerequisites**

* [Docker][docker] must be installed
* Authenticate with the SMITH Container Registry  
  The SMITH container registry is not publicly accessible. Use `docker login creg.smith.care` to
  authenticate. DIC
  specific credentials can be obtained from [Jonas Wagner][@jwagner], [Alexander Kiel][@akiel] and
  [Sebastian Stäubert][@sstaeubert]

## Running with Java

Executable jar files for each agent are distributed to the
SMITH [GitLab package registry][packages]. For executing the
different components, example command line calls will be provided using Java 17 as runtime.

**Prerequisites**

* Java 17 must be installed  
  The provided docker images and end-to-end-tests use [temurin-jdk17][temurin]

## Configuration

The documentation will provide configuration file templates containing placeholders, that will also
indicate the
function of configuration options. The directory structure of the provided files should be left
intact, as the agents
expect them to be in specific places.

## Components

| Component             |  Execute with Java   |  Execute with Docker   |  Documentation   |
|-----------------------|:--------------------:|:----------------------:|:----------------:|
| Clinical Domain Agent | [Download][cda/java] | [Download][cda/docker] | [README](cda.md) |
| Research Domain Agent | [Download][rda/java] | [Download][rda/docker] | [README](rda.md) |
| Trust Center Agent    | [Download][tca/java] | [Download][tca/docker] | [README](tca.md) |

[@jwagner]: mailto:jonas.wagner@uni-leipzig.de

[@akiel]: mailto:alexander.kiel@uni-leipzig.de

[@sstaeubert]: mailto:sebastian.staeubert@imise.uni-leipzig.de

[docker]: https://docs.docker.com/engine/install/

[temurin]: https://adoptium.net/de/temurin/releases/?version=17

[packages]: https://git.smith.care/smith/fhir-transfer-services/fhir-transfer-agent/-/packages

[cda/java]: https://git.smith.care/api/v4/projects/135/packages/generic/clinical-domain-agent/4.2.0/clinical-domain-agent-java.zip

[cda/docker]: https://git.smith.care/api/v4/projects/135/packages/generic/clinical-domain-agent/4.2.0/clinical-domain-agent-docker.zip

[tca/java]: https://git.smith.care/api/v4/projects/135/packages/generic/trust-center-agent/4.2.0/trust-center-agent-java.zip

[tca/docker]: https://git.smith.care/api/v4/projects/135/packages/generic/trust-center-agent/4.2.0/trust-center-agent-docker.zip

[rda/java]: https://git.smith.care/api/v4/projects/135/packages/generic/research-domain-agent/4.2.0/research-domain-agent-java.zip

[rda/docker]: https://git.smith.care/api/v4/projects/135/packages/generic/research-domain-agent/4.2.0/research-domain-agent-docker.zip
