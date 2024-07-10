# FTS Development

Welcome to the developer documentation for the FHIR Transfer Services project! This document is
intended to guide developers through the codebase, architecture, and development practices.

## Repository Structure

The project follows a structured organization to enhance readability and maintainability.

- `documentation/`  
  Markdown files with examples and detailed documentation for users and developers.
  Includes user guides, developer guides, release steps, and more.

- `clinicaldomainagent/`
  Java code, Dockerfile, CI config snippets, and Maven configuration (`pom.xml`) for the Clinical Domain Agent.

- `researchdomainagent/`  
  Java code, Dockerfile, CI config snippets, and Maven configuration (`pom.xml`) for the Research Domain Agent.

- `trustcenteragent/`  
  Java code, Dockerfile, CI config snippets, and Maven configuration (`pom.xml`) for the Trust Center Agent.

- `end-to-end-tests/`
  End-to-end (e2e) compose configuration for end-to-end tests. Contains end-to-end tests
  using [Test Containers][testcontainers] to transferProcessInstance tests executed by Maven.

- `.gitlab/`  
  Contains GitLab CI (Continuous Integration) process helper files, see also the
  top-level [`.gitlab-ci.yml`](.gitlab-ci.yml).

### Prerequisites

Installed:

- Linux (not tested for Windows)
- Java 17
- Maven
- docker
- docker-compose

### Build

Run the build script:

```shell
$ ./build.sh
```

### Run

There are three ways to transferProcessInstance the agents:

- with plain java `jars`
- with docker-compose
- with docker image

#### jars

Download latest release `.jar` files from
the [Gitlab Package Registry](https://git.smith.care/smith/fhir-transfer-services/fhir-transfer-agent/-/packages).  
Run:

```shell
java -jar /path/to/agent.jar --spring.config.location=/path/to/application.yaml
```

Be aware that `application.yaml` must be configured accordingly (e.g. path to deidentiFHIR profiles
must be set).  
See the following section for example configurations contained in the `end-to-end-tests` folder.

#### end-to-end Tests

Start the docker containers with:

```shell
docker-compose up
```

in the `end-to-end-tests` folder.  
This will transferProcessInstance an entire environment with

- `clinical-domain-agent`
- `research-domain-agent`
- `trust-center-agent`
- `gPAS`, `gICS` and `E-PIX`
- FHIR servers for clinical and research domain

To adapt the setup to your own needs, you can take the `end-to-end-tests/docker-compose.yml` file as a starting point.

### Project Specific Configuration

The FTSs are quite flexible and allow the execution of different processes for different projects on
the same instances
of the CDA and RDA. To configure the behaviour for each project you must provide project specific
parameters that will
be loaded from the application.yaml at runtime based on the concrete value specified in
the `PROJECT_ID` process
variable.
