# Runner <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This page documents the `runner` section of the FTSnext agent configuration file
(`application.yaml`). It defines parameters that control concurrency and the
lifecycle of processes managed by FTSnext

## Configuration Example

```yaml
runner:
  maxConcurrentPatients: 8
  maxSendConcurrency: 2
  maxConcurrentProcesses: 4
  cohortSelectionConcurrency: 4
  processTtl: P1D
```

## Fields

### `maxConcurrentPatients` <Badge type="warning" text="Since 5.7" />

* **Description**: The number of patients whose data is fetched and deidentified ahead of the send
  stage. This bounds how far the pipeline may run ahead of the RDA, and is the upper bound on
  [`maxSendConcurrency`](#maxsendconcurrency): the agent fails to start if `maxSendConcurrency`
  exceeds it.
* **Type**: Integer
* **Default**: `8`
* **Minimum**: `1`
* **Example**:
  ```yaml
  runner:
    maxConcurrentPatients: 16
  ```

### `maxSendConcurrency` <Badge type="warning" text="Since 5.0" />

* **Description**: The maximum number of concurrent bundles that can be sent in parallel. This is
  usually the bottleneck of a transfer and should match the target Blaze's transaction capacity.
  Must not exceed [`maxConcurrentPatients`](#maxconcurrentpatients); raise that value first when
  increasing this one.
* **Type**: Integer
* **Default**: `2`
* **Minimum**: `1`
* **Example**:
  ```yaml
  runner:
    maxConcurrentPatients: 16  # must be raised alongside maxSendConcurrency
    maxSendConcurrency: 16
  ```

### `maxConcurrentProcesses` <Badge type="warning" text="Since 5.0" />

* **Description**: The maximum number of processes that can run concurrently.
* **Type**: Integer
* **Default**: `4`
* **Example**:
  ```yaml
  runner:
    maxConcurrentProcesses: 10
  ```

### `cohortSelectionConcurrency` <Badge type="warning" text="Since 5.7" />

* **Description**: The number of concurrent workers used to group patients with their consents
  during cohort selection. This grouping is CPU-bound and runs on a dedicated scheduler that this
  value also sizes; the scheduler is shared across all concurrent processes, so this caps the total
  threads cohort selection may use rather than being per-process. Raise it on hosts with more cores
  if cohort selection is a bottleneck.
* **Type**: Integer
* **Default**: `4`
* **Example**:
  ```yaml
  runner:
    cohortSelectionConcurrency: 8
  ```

### `processTtl` <Badge type="warning" text="Since 5.0" />

* **Description**: The time-to-live (TTL) for a process, defined as an ISO-8601 duration (e.g.,
  `P1D` for 1 day).
* **Type**: String (ISO-8601 duration format)
* **Default**: `P1D`
* **Example**:
  ```yaml
  runner:
    processTtl: PT3H  # 3 hours
  ```

## Notes

* **Concurrency Constraint**: `maxSendConcurrency` must not exceed `maxConcurrentPatients`. The
  agent validates this at startup and fails with
  `runner.maxSendConcurrency must not exceed runner.maxConcurrentPatients` if violated. Since
  `maxConcurrentPatients` defaults to `8`, setting `maxSendConcurrency` above `8` without also
  raising `maxConcurrentPatients` prevents the agent from starting.
* **ISO-8601 Duration Format**: For more details on the duration format, refer
  to [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601#Durations).
* Ensure that the `processTtl` value is reasonable to avoid resource exhaustion due to long-lived
  processes.
