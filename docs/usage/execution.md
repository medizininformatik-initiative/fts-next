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
curl -X POST https://cd-agent:8080/api/v2/process/example/start
```

### Manual Cohort

The other option is to have a list of IDs as payload with the start request, e.g.

```shell
curl -X POST --data '["id1", "id2", "id3"]' -H "Content-Type: application/json" \
  https://cd-agent:8080/api/v2/process/example/start
```

[API Reference for Start Endpoint](/open-api/cd-openapi.html#post-/api/v2/process/-project-/start)

## Transfer Status

The response's Content-Location header contains a URL with the transfer status, e.g.

```shell
curl "https://cd-agent:8080/api/v2/process/status/52792219-b966-44bf-bc1b-c0eafbe8ead0"
```

The status response looks like this:

<!--@formatter:off-->
```json
{
  "processId": "e17d319e-d967-467e-8c8a-0c464bb14951",
  "phase": "COMPLETED",
  "createdAt": [ 2024, 11, 13, 8, 35, 35, 262354492 ],
  "finishedAt": [ 2024, 11, 13, 8, 36, 17, 358171815 ],
  "totalPatients": 100,
  "totalBundles": 119,
  "deidentifiedBundles": 118,
  "sentBundles": 118,
  "skippedBundles": 0
}
```
<!--@formatter:on-->

| Field                 | Description                                                                              |
|-----------------------|------------------------------------------------------------------------------------------|
| `processId`           | Process ID                                                                               |
| `phase`               | Status of the process (`QUEUED`, `RUNNING`, `COMPLETED`)                                 |
| `createdAt`           | Point in time when the process was created                                               |
| `finishedAt`          | Point in time when the process finished                                                  |
| `totalPatients`       | Total number of patients to be processed, may change while the process is running        |
| `totalBundles`        | Total number of bundles to be processed                                                  |
| `deidentifiedBundles` | Number of bundles after deidentification                                                 |
| `sentBundles`         | Number of bundles sent to RDA                                                            |
| `skippedBundles`      | Number of skipped bundles; if greater than zero, investigate logs to determine the cause |

[API Reference for Status Endpoint](/open-api/cd-openapi.html#get-/api/v2/process/status/-processId-)

## Monitoring

FTSnext provides a monitoring docker container with Grafana dashboards that show some metrics.
To work, the agents' IP addresses in `monitoring/prometheus.yml` must be set accordingly.
