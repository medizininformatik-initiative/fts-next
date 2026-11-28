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
  "excludedPatients": 0,
  "error": null
}
```
<!--@formatter:on-->

| Field                 | Description                                                                                                                                                              |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `processId`           | Process ID                                                                                                                                                               |
| `phase`               | Status of the process, see [Phases](#phases)                                                                                                                             |
| `createdAt`           | Point in time when the process was created (ISO-8601 with offset; see [Time Zone](../configuration/time-zone))                                                           |
| `finishedAt`          | Point in time when the process finished (ISO-8601 with offset; see [Time Zone](../configuration/time-zone))                                                              |
| `totalPatients`       | Total number of consented patients to be processed, may change while the process is running                                                                              |
| `totalBundles`        | Total number of bundles to be processed                                                                                                                                  |
| `deidentifiedBundles` | Number of bundles after deidentification                                                                                                                                 |
| `sentBundles`         | Number of bundles sent to RDA                                                                                                                                            |
| `skippedBundles`      | Number of bundles skipped because a step failed; if greater than zero, query the [failed patients endpoint](#failed-patients) for details                                |
| `excludedPatients`    | Number of patients excluded during cohort selection, e.g. for a faulty consent; if greater than zero, query the [failed patients endpoint](#failed-patients) for details |
| `error`               | Message of the error that stopped the process in phase `FATAL`, `null` otherwise                                                                                         |

#### Phases

| Phase                  | Meaning                                                                                                           |
|------------------------|-------------------------------------------------------------------------------------------------------------------|
| `QUEUED`               | The process waits for a free slot                                                                                 |
| `RUNNING`              | The process transfers data                                                                                        |
| `COMPLETED`            | The process finished, and no step failed for any patient; patients may have been excluded, see `excludedPatients` |
| `COMPLETED_WITH_ERROR` | The process finished, but a step failed for at least one patient, see `skippedBundles`                            |
| `FATAL`                | The process stopped, e.g. because the cohort selection failed                                                     |

[API Reference for Status Endpoint](/open-api/cd-openapi.html#get-/api/v2/process/status/-processId-)

### Failed Patients

When `skippedBundles` or `excludedPatients` is greater than zero, the failed patients
endpoint returns which patients failed or were excluded, and at which step. Patients
excluded at step `SELECT_COHORT` are explained in
[Excluded Patients](#excluded-patients):

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

### Excluded Patients

The cohort selection excludes a patient for one of these reasons, given as `errorMessage`
with step `SELECT_COHORT`:

| Reason                                        | Meaning                                                                                                         | Reported                    |
|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------|-----------------------------|
| `Consent for policy … has no start date`      | The only consent for a required policy cannot be evaluated; fix the consent, e.g. in gICS                       | Always                      |
| `Consent for policy … has an unreadable date` | As above, its start or end date cannot be read                                                                  | Always                      |
| `No consent covering all required policies`   | The patient has consents, but no active consent covers all required policies, e.g. all are inactive or rejected | For a list of patients only |
| `No consent found`                            | The consent source returned no consent at all for the patient, or not the patient at all                        | For a list of patients only |

If several required policies have a faulty consent, the reasons are joined by `; `. A
patient is excluded for a faulty consent only if every policy it lacks has one; otherwise
correcting the consent would not make the patient transferable.

If the cohort selection fails (phase `FATAL`), only the patients excluded for a faulty
consent up to that point are reported. Listed patients are not reported as without
consent, as the selection did not return all patients.

`No consent found` can also mean that the clinical domain agent cannot match the
consent source's patients to the requested identifiers. Check these warnings in the log of
the clinical domain agent:

* `Ignoring N patients without an identifier of the configured patient identifier system`:
  the patients returned by the consent source carry no identifier of the configured
  system, e.g. `patientIdentifierSystem` or `signerIdType` does not match the consent
  source.
* `Ignoring N consents that reference no patient of the bundle`: with a FHIR server as
  consent source, consents must reference their patient by a literal reference, e.g.
  `Patient/123`, not by identifier only. The search for consents by patient identifier
  does not find such consents at all, so the patient is reported as `No consent found`
  without this warning.

A patient with several identifiers of the configured system is transferred and reported
under the requested one. If several of these identifiers are requested, the patient is
transferred once, and the other requested identifiers are reported as `No consent found`.

### Finding the Patient of a Log Message

The log messages of the clinical domain agent name no patients: neither patient
identifiers, nor FHIR resource IDs, nor consent dates. A message about a failed or
excluded patient names the process and, for errors, only the types of the error and its
causes. The failed patients endpoint of that process names the patient and the error
message:

<!--@formatter:off-->
```text
… ERROR … DefaultTransferProcessRunner : [Process t5K0Qs] Failed to select data for a patient: RetryExhaustedException(WebClientRequestException(AnnotatedConnectException(ConnectException))), see /api/v2/process/status/t5K0Qs/failed_patients
… WARN  … CohortReport                 : [Process t5K0Qs] Patients excluded from cohort: Consent for policy 2.16.840.1.113883.3.1937.777.24.5.3.6 has no start date (1)
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
    "errorMessage": "Retries exhausted: 3/3"
  }
]
```
<!--@formatter:on-->

Each entry matches a log message of the same `step`: `SELECT_COHORT` entries match the
reason of a `Patients excluded from cohort` message, which counts the patients excluded for
that reason, other entries a `Failed to …` message.
Failed patients are kept as long as the process status, see
[`processTtl`](../configuration/runner#processttl) in the runner configuration.

A failed process logs the types of its error and causes as well, and the `error` field of
its [status](#transfer-status) gives the message:

<!--@formatter:off-->
```text
… ERROR … DefaultTransferProcessRunner : [Process t5K0Qs] Cohort selection failed: RetryExhaustedException(WebClientRequestException(AnnotatedConnectException(ConnectException))), see /api/v2/process/status/t5K0Qs
```
<!--@formatter:on-->

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
