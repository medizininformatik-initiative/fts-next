# Execution

A transfer process is initiated by calling the project's start endpoint
of the CDA.
The project name is given by its project configuration filename,
e.g. for the example project from the template it would be example.

Assuming that cd-agent points to the IP address of the CDA then the endpoint is
`https://cd-agent:8080/api/v2/process/example/start`.

## Transfer Modes

FTSnext has two transfer modes:

### All Consented Patients

If no data is passed to the start request
the list of patients is fetched from gICS
and all patients with consent are transferred.

For example, to start the transfer of the template's example project run:

```shell
curl -sSfX POST "https://cd-agent:8080/api/v2/process/example/start"
```

### Manual Cohort

The other option is to have a list of IDs as payload with the start request, e.g.

```shell
curl -sSf --data '["id1", "id2", "id3"]' -H "Content-Type: application/json" \
  "https://cd-agent:8080/api/v2/process/example/start"
```

[API Reference for Start Endpoint](/open-api/cd-openapi.html#post-/api/v2/process/-project-/start)

## Transfer Status

The response's Content-Location header contains a URL with the transfer status, e.g.

```shell
curl -sSf "https://cd-agent:8080/api/v2/process/status/52792219-b966-44bf-bc1b-c0eafbe8ead0"
```

The status response looks like this:

<!--@formatter:off-->
```json
{
  "processId": "e17d319e-d967-467e-8c8a-0c464bb14951",
  "phase": "COMPLETED",
  "createdAt": "2024-11-13T09:35:35.262+01:00",
  "finishedAt": "2024-11-13T09:36:17.358+01:00",
  "totalPatients": 100,
  "totalBundles": 119,
  "deidentifiedBundles": 118,
  "sentBundles": 118,
  "skippedBundles": 0,
  "skippedPatients": 0
}
```
<!--@formatter:on-->

| Field                 | Description                                                                                                                                                              |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `processId`           | Process ID                                                                                                                                                               |
| `phase`               | Status of the process (`QUEUED`, `RUNNING`, `COMPLETED`)                                                                                                                 |
| `createdAt`           | Point in time when the process was created (ISO-8601 with offset; see [Time Zone](../configuration/time-zone))                                                           |
| `finishedAt`          | Point in time when the process finished (ISO-8601 with offset; see [Time Zone](../configuration/time-zone))                                                              |
| `totalPatients`       | Total number of consented patients to be processed, may change while the process is running                                                                              |
| `totalBundles`        | Total number of bundles to be processed                                                                                                                                  |
| `deidentifiedBundles` | Number of bundles after deidentification                                                                                                                                 |
| `sentBundles`         | Number of bundles sent to RDA                                                                                                                                            |
| `skippedBundles`      | Number of skipped bundles; if greater than zero, query the [failed patients endpoint](#failed-patients) for details                                                      |
| `skippedPatients`     | Number of patients excluded during cohort selection, e.g. for a faulty consent; if greater than zero, query the [failed patients endpoint](#failed-patients) for details |

[API Reference for Status Endpoint](/open-api/cd-openapi.html#get-/api/v2/process/status/-processId-)

### Failed Patients

When `skippedBundles` or `skippedPatients` is greater than zero, the failed patients
endpoint returns which patients failed and at which step. Patients excluded at step
`SELECT_COHORT` have a faulty consent, e.g. one without start date. When a transfer is
started for a list of patients, each listed patient without consent covering all required
policies is reported there as well:

```shell
curl -sSf "https://cd-agent:8080/api/v2/process/status/52792219-b966-44bf-bc1b-c0eafbe8ead0/failed_patients"
```

<!--@formatter:off-->
```json
[
  {
    "patientIdentifier": "patient-007",
    "step": "SELECT_COHORT",
    "errorMessage": "Consent for policy 2.16.840.1.113883.3.1937.777.24.5.3.6 has no start date"
  },
  {
    "patientIdentifier": "patient-001",
    "step": "SELECT_DATA",
    "errorMessage": "Connection refused"
  },
  {
    "patientIdentifier": "patient-042",
    "step": "DEIDENTIFY",
    "errorMessage": "Cannot deidentify bundle"
  }
]
```
<!--@formatter:on-->

| Field               | Description                                                                                            |
|---------------------|--------------------------------------------------------------------------------------------------------|
| `patientIdentifier` | Identifier of the patient whose transfer failed                                                        |
| `step`              | Processing step where the error occurred (`SELECT_COHORT`, `SELECT_DATA`, `DEIDENTIFY`, `SEND_BUNDLE`) |
| `errorMessage`      | Error message describing the failure                                                                   |

[API Reference for Failed Patients Endpoint](/open-api/cd-openapi.html#get-/api/v2/process/status/-processId-/failed_patients)

### Finding the Patient of a Log Message

The log messages of the clinical domain agent name no patients: neither patient
identifiers, nor FHIR resource IDs, nor consent dates. A message about a failed or
excluded patient names the process and, for errors, only the type of the error. The
failed patients endpoint of that process names the patient and the full error message:

<!--@formatter:off-->
```text
… ERROR … DefaultTransferProcessRunner : [Process t5K0Qs] Failed to select data for a patient: WebClientRequestException, see /api/v2/process/status/t5K0Qs/failed_patients
… WARN  … DefaultTransferProcessRunner : [Process t5K0Qs] Patient excluded from cohort: Consent for policy 2.16.840.1.113883.3.1937.777.24.5.3.6 has no start date
```
<!--@formatter:on-->

Query the path from the message on the clinical domain agent:

```shell
curl -sSf "https://cd-agent:8080/api/v2/process/status/t5K0Qs/failed_patients"
```

<!--@formatter:off-->
```json
[
  {
    "patientIdentifier": "patient-007",
    "step": "SELECT_COHORT",
    "errorMessage": "Consent for policy 2.16.840.1.113883.3.1937.777.24.5.3.6 has no start date"
  },
  {
    "patientIdentifier": "patient-001",
    "step": "SELECT_DATA",
    "errorMessage": "Connection refused: hds/10.0.0.12:8080"
  }
]
```
<!--@formatter:on-->

Each entry matches a log message of the same `step`: `SELECT_COHORT` entries match the
reason of a `Patient excluded from cohort` message, other entries a `Failed to …` message.
Failed patients are kept as long as the process status, see
[`processTtl`](../configuration/runner#processttl) in the runner configuration.

::: warning
Two kinds of log output can still contain patient data, so keep them off in production:

* Stack traces, which the clinical domain agent logs only at level `TRACE`. Their error
  messages may contain request URLs with patient IDs.
* Messages of the libraries the agent uses, e.g. Spring's web client, which logs request
  URLs at level `DEBUG`.
  :::

## Monitoring

FTSnext provides a monitoring docker container with Grafana dashboards that show some metrics.
To work, the agents' IP addresses in `monitoring/prometheus.yml` must be set accordingly.
