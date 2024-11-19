# Logging <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /><Badge type="tip" text="Trust Center Agent" />

This documentation provides guidance on configuring the logging levels for various application
components, using the provided YAML snippet as an example.

## YAML Configuration Example

Below is the sample YAML snippet:

```yaml
logging.level:
  # Log level for the care.smith.fts package
  care.smith.fts: INFO
```

### Explanation

- **`logging.level`**: This is the main configuration key used to define the log levels for specific
  application components or packages.
- **`care.smith.fts`**: A specific logging prefix for the `care.smith.fts` package. The log level
  for this package is set to `INFO` in the example.

To enable or modify the logging level, uncomment and adjust the relevant package's entry.

## Log Levels

The following log levels can be assigned to a logging prefix:

- **TRACE**: Fine-grained informational events useful for debugging.
- **DEBUG**: General debugging information.
- **INFO**: Informational messages indicating normal operations.
- **WARN**: Warnings indicating potentially harmful situations.
- **ERROR**: Error events that might allow the application to continue running.
- **FATAL**: Severe error events causing premature application termination.

## Significant Logging Prefixes

The table below outlines common logging prefixes and their typical purposes:

| **Logging Prefix**         | **Description**                                                          | **Example Log Level** |
|----------------------------|--------------------------------------------------------------------------|-----------------------|
| `care.smith.fts`           | Handles functionalities in the FTS module of the care.smith application. | `INFO`                |

## How to Modify Log Levels

To customize the log level for a specific package:

1. Uncomment the relevant entry in the `logging.level` section.
2. Set the desired log level from the available options.
3. Save the configuration file and restart the application for changes to take effect.

**Example:**

```yaml
logging.level:
  care.smith.fts: DEBUG
  care.smith.auth: WARN
```

This configuration sets the `care.smith.fts` package to `DEBUG` and the `care.smith.auth` package to
`WARN`.

For additional details, refer to the application's logging framework documentation.

## References

* 