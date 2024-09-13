# Clinical Domain Agent (CDA)

The CDA's task is to select a cohort of patients, de-identify their data, and transfer it to the
research domain.

The transfer process consists of four steps, namely CohortSelection, DataSelection,
De-identification, and Bundle sending.
During CohortSelection a list of patients that gave the necessary consents as required in the
process definition.
The DataSelection fetches the patient's data bundles from the HDS,
which are pseudonymized and date shifted during De-identification and finally sent to the research
domain.

## Setup

### Docker

```shell
TODO
docker pull ftsnext/cda # or whatever
```

## Configuration

The configuration is separated into server and project parts.
The server is configured once via its `application.yaml` while each project has its own config file.

### Server Configuration

TODO: add comments to yaml

#### application.yml
```yaml
projects:
  directory: "src/test/resources/projects" # path with project config files

spring:
  ssl:
    bundle:
      pem:
        server:
          keystore:
            certificate: target/test-classes/server.crt
            private-key: target/test-classes/server.key
          truststore:
            certificate: target/test-classes/ca.crt
        client:
          truststore:
            certificate: target/test-classes/ca.crt

# Concurrency settings
runner:
  # The concurrency for a transfer process is:  
  # processConcurrency = maxConcurrency / maxProcesses
  maxConcurrency: 128
  maxProcesses: 4
  processTtl: P1D # how long to keep the finished process in memory, ISO8601

server:
  ssl:
    bundle: server

test:
  webclient:
    default:
      ssl:
        bundle: client

security:
  endpoints:
    - path: /api/v2/**
      role: client

management:
  endpoints:
    web:
      exposure:
        include: [ "health", "info", "prometheus" ]

  metrics:
    distribution:
      slo:
        http.server.requests: 25,100,250,500,1000,10000
        http.client.requests: 25,100,250,500,1000,2000,3000,4000,5000,6000,7000,8000,9000,10000
        deidentify: 25,100,250,500,1000,10000
```

### Project Configuration

This section describes the configuration settings for a medical data processing system that handles
cohort selection, data selection, de-identification, and data transmission to a research domain.

Yaml files put into the directory defined by `projects.directory` of the server configuration are
considered project configuration files.
They must fulfil the following schema:

#### example.yml
```yaml
cohortSelector:
  trustCenterAgent:
    server:
      baseUrl: http://tc-agent:8080
    domain: MII # domain in gICS
    patientIdentifierSystem: "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym"
    policySystem: "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy"
    policies: [ "IDAT_erheben", "IDAT_speichern_verarbeiten", "MDAT_erheben", "MDAT_speichern_verarbeiten" ]

dataSelector:
  everything:
    fhirServer:
      baseUrl: http://cd-hds:8080/fhir
    resolve:
      patientIdentifierSystem: http://fts.smith.care

deidentificator:
  deidentifhir:
    tca:
      server:
        baseUrl: http://tc-agent:8080
      domains:
        # domains in gPAS for pseudonym and salt storage
        # the same domain may be used for all three
        pseudonym: MII
        salt: MII-ID-Salt
        dateShift: MII-DateShift-Salt
    maxDateShift: P14D # ISO 8601
    deidentifhirConfig: /app/config/deidentifhir/CDtoTransport.profile
    scraperConfig: /app/config/deidentifhir/IDScraper.profile

bundleSender:
  researchDomainAgent:
    server:
      baseUrl: http://rd-agent:8080
    project: example # name of project in the research domain
```

The domains in gICS and gPAS must be present, i.e. they are not created automatically and the
transfer process will fail, if they are not present.

The system consists of four main components:

1. Cohort Selector
2. Data Selector
3. Deidentificator
4. Bundle Sender

#### Cohort Selector

The cohort selector component interfaces with the Trust Center Agent to manage patient cohorts and
their consent policies.

```yaml
cohortSelector:
  trustCenterAgent:
    server:
      baseUrl: http://tc-agent:8080
      domain: MII
    patientIdentifierSystem: "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym"
    policySystem: "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy"
    policies: [ "IDAT_erheben", "IDAT_speichern_verarbeiten", "MDAT_erheben", "MDAT_speichern_verarbeiten" ]
```

**Configuration Parameters:**

- `baseUrl`: The Trust Center Agent server endpoint
- `domain`: The domain identifier in gICS
- `patientIdentifierSystem`: The system URI for patient pseudonyms
- `policySystem`: The system URI for consent policies
- `policies`: Array of required consent policies

#### Data Selector

The data selector component retrieves patient data from the FHIR server.

```yaml
dataSelector:
  everything:
    fhirServer:
      baseUrl: http://cd-hds:8080/fhir
    resolve:
      patientIdentifierSystem: http://fts.smith.care
```

**Configuration Parameters:**

- `baseUrl`: The FHIR server endpoint
- `patientIdentifierSystem`: The system URI for resolving patient identifiers

#### Deidentificator

The Deidentificator component handles data de-identification using the Deidentifhir service.

```yaml
deidentificator:
  deidentifhir:
    tca:
      server:
        baseUrl: http://tc-agent:8080
      domains:
        pseudonym: MII
        salt: MII-ID-Salt
        dateShift: MII-DateShift-Salt
    maxDateShift: P14D
    deidentifhirConfig: /app/config/deidentifhir/CDtoTransport.profile
    scraperConfig: /app/config/deidentifhir/IDScraper.profile
```

**Configuration Parameters:**

- `baseUrl`: The Trust Center Agent server endpoint
- `domains`: Domain configurations for different aspects of de-identification
- `pseudonym`: Domain for pseudonym storage
- `salt`: Domain for pseudonym salt storage
- `dateShift`: Domain for date shift salt storage
- `maxDateShift`: Maximum date shift in ISO 8601 duration format
- `deidentifhirConfig`: Path to the Deidentifhir configuration profile
- `scraperConfig`: Path to the ID scraper configuration profile

#### Bundle Sender

The bundle sender component transmits the processed data to the research domain.

```yaml
bundleSender:
  researchDomainAgent:
    server:
      baseUrl: http://rd-agent:8080
    project: example
```

**Configuration Parameters:**

- `baseUrl`: The Research Domain Agent server endpoint
- `project`: The target project name in the research domain

## Usage

For each project, the CDA offers an endpoint to start the transfer process.
The caller may add a list of patient IDs to be transferred to the request's body.
If no data is submitted all consents from gICS are fetched and processed.

To start a transfer process for the project $PROJECT run

```shell
curl -X POST -w "%header{Content-Location}" http://cd-agent:8080/api/v2/process/${PROJECT}/start
```

or

```shell
TODO: check if correct
PATIENT_IDS=["id1", "id2", "id3"]
curl -X POST --data '${PATIENT_IDS}' -H "Content-Type: application/json" \
    -w "%header{Content-Location}" "${cd_agent_base_url}/api/v2/process/${1}/start"
```

The Content-Location field of the response's header contains a link to retrieve the process's
status.

**Example**

```shell
curl -sf "http://cd-agent:8080/api/v2/process/status/e17d319e-d967-467e-8c8a-0c464bb14951"
{"processId":"e17d319e-d967-467e-8c8a-0c464bb14951","phase":"COMPLETED","createdAt":[2024,11,13,8,35,35,262354492],"finishedAt":[2024,11,13,8,36,17,358171815],"totalPatients":100,"totalBundles":119,"deidentifiedBundles":118,"sentBundles":118,"skippedBundles":0}  
```

### Status Fields Description

processId
: process ID

phase
: QUEUED, RUNNING, COMPLETED

createdAt
: point in time when the process was created

finishedAt
: point in time when the process finished

totalPatients
: Total number of patients to be processed, may change while the process is running

totalBundles
: Total number of bundles to be processed

deidentifiedBundles
: Number of bundles after deidentification

sentBundles
: Number of bundles sent to RDA

skippedBundles
: Number of skipped bundles

If the number of skippedBundles is greater than zero one should look into the logs to find the
cause.
