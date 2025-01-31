# Runner <Badge type="tip" text="Clinical Domain Agent" />

This page documents the `runner` section of the FTSnext agent configuration file
(`application.yaml`). It defines parameters that control concurrency and the
lifecycle of processes managed by FTSnext

## Configuration Example

```yaml
runner:
  maxSendConcurrency: 32
  maxConcurrentProcesses: 4
  processTtl: P1D
```

## Fields

### `maxSendConcurrency`

* **Description**: The maximum number of concurrent bundles that can be sent in parallel.
* **Type**: Integer
* **Default**: `32`
* **Example**:
  ```yaml
  runner:
    maxSendConcurrency: 50
  ```

### `maxConcurrentProcesses`

* **Description**: The maximum number of processes that can run concurrently.
* **Type**: Integer
* **Default**: `4`
* **Example**:
  ```yaml
  runner:
    maxConcurrentProcesses: 10
  ```

### `processTtl`

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

* **ISO-8601 Duration Format**: For more details on the duration format, refer
  to [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601#Durations).
* Ensure that the `processTtl` value is reasonable to avoid resource exhaustion due to long-lived
  processes.
