# Bundle Sender <Badge type="tip" text="Clinical Domain Agent" />

This document describes the configuration options available for managing the `bundleSender` section
of the configuration file. This section is responsible for defining how bundles are sent after being
processed by the CDA.

## Configuration Structure

The `cohortSelector` section allows different implementations to be used for selecting the transfer
cohort, at the moment there is only one implementation available out-of-the-box: 
`researchDomainAgent`.

```yaml
bundleSender:
  researchDomainAgent:
    server:
      baseUrl: http://rd-agent:8080
    project: example
```

## Fields

### `researchDomainAgent`

This implementation sends processed bundles to the RDA, where another deidentification is realized 
and data is stored in a fhir server.

#### `server`

* **Description**: Contains settings for connecting to the RDA server.
* **Type**: Object

##### `baseUrl`

* **Description**: Specifies the base URL of the RDA server.
* **Type**: String
* **Example**:
  ```yaml
      server:
        baseUrl: "http://custom-rda-server:9090"
  ```

#### `project`

* **Description**: Specifies the project configuration to use in the research domain.
* **Type**: String
* **Example**:
  ```yaml
      project: "customProject"
  ```

## Notes

* The `baseUrl` field must be a valid URL pointing to the RDA server.
* The `project` field must match a valid project configuration as defined in the RDA setup. For more
  details, refer to the [Project Configuration documentation](../rd-agent/project).
