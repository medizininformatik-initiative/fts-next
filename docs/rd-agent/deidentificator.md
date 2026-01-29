# Deidentificator <Badge type="tip" text="Research Domain Agent" />

This document describes the configuration options available for managing deidentification settings
in the `deidentificator` section of the project configuration file.

The RDA deidentificator is responsible for:
1. Requesting research mappings from the TCA (including tID→shiftedDate mappings)
2. Replacing transport IDs (tIDs) with research pseudonyms (sIDs)
3. Resolving date transport ID extensions to shifted dates and removing the extensions

For details on the DateShift-ID Pattern, see the [De-Identification](../details/deidentification)
documentation.

## Configuration Example

The `deidentificator` section allows different implementations to be used for pseudonymizing and
anonymizing patient data. At the moment there is only one implementation available out-of-the-box:
`deidentifhir`

```yaml
deidentificator:
  deidentifhir:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
        auth: [ ... ]
        ssl: [ ... ]
    deidentifhirConfig: /app/config/deidentifhir/TransportToRD.profile
```

## Fields

### `deidentifhir`

This implementation uses [deidentifhir](https://github.com/UMEssen/DeidentiFHIR) to accomplish
deidentification of FHIR bundles.

#### `trustCenterAgent.server`

* **Description**: Contains settings for connecting to the Trust Center Agent (TCA).
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
    trustCenterAgent:
      server:
        baseUrl: http://custom-tc-agent:9000
        auth: [ ... ]
        ssl: [ ... ]
  ```

#### `deidentifhirConfig`

* **Description**: Path to the DeidentiFHIR configuration file. If using a Docker container, the
  path must be mounted into the container.
* **Type**: String
* **Example**:
  ```yaml
    deidentifhirConfig: /custom/path/TransportToRD.profile
  ```

## Notes

* Mount the configuration files (`deidentifhirConfig`) into the Docker container if the agent runs
  in a containerized environment. Ensure the paths are accessible to the agent at runtime.
