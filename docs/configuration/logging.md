# Logging <Badge type="tip" text="All Agents" />

This page documents the `logging` section of the FTSnext agent configuration file
(`application.yaml`). It is used to define logging behaviour of the agents, and allows
for fine-grained configuration, relying upon [Spring Boot logging][spring-logging].

## Configuration Example

```yaml
logging.level:
  care.smith.fts: INFO
```

### Explanation:
* `logging.level`: This is the main configuration key used to define the log levels for specific
  application components or packages.
* `care.smith.fts`: A specific logging prefix for the `care.smith.fts` package. The log level
  for this package is set to `INFO` in the example.

To enable or modify the logging level, uncomment and adjust the relevant package's entry.

## Log Levels

The following log levels can be assigned to a logging prefix:

* `TRACE`: Fine-grained informational events useful for debugging.
* `DEBUG`: General debugging information.
* `INFO`: Informational messages indicating normal operations.
* `WARN`: Warnings indicating potentially harmful situations.
* `ERROR`: Error events that might allow the application to continue running.
* `FATAL`: Severe error events causing premature application termination.

## Significant Logging Prefixes

The table below outlines common logging prefixes and their typical purposes:

| **Logging Prefix** | **Description**                                                          | **Example Log Level** |
|--------------------|--------------------------------------------------------------------------|-----------------------|
| `care.smith.fts`   | Handles functionalities in the FTS module of the care.smith application. | `INFO`                |

## How to Modify Log Levels

To customize the log level for a specific package:

1. Uncomment the relevant entry in the `logging.level` section.
2. Set the desired log level from the available options.
3. Save the configuration file and restart the application for changes to take effect.

**Example:**

```yaml
logging.level:
  care.smith.fts: INFO
  care.smith.fts.util: WARN
```

This configuration sets the `care.smith.fts` package to `INFO` and the `care.smith.fts.util` package
to `WARN`.

For additional details, refer to the application's logging framework documentation.

## References

* [Spring Boot Logging][spring-logging]

[spring-logging]: https://docs.spring.io/spring-boot/reference/features/logging.html