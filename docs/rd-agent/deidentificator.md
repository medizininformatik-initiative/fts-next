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
anonymizing patient data. The recommended implementation is `idMapper`:

```yaml
deidentificator:
  idMapper:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
        auth: [ ... ]
        ssl: [ ... ]
```

## Fields

### `idMapper`

Maps transport IDs to research pseudonyms using the HAPI FHIR object model. This implementation
replaces resource IDs, references, and identifier values, and restores shifted dates from transport
ID extensions.

#### `trustCenterAgent`

Connects to the Trust Center Agent to resolve transport IDs to research pseudonyms via the TCA's
secure mapping endpoint.

##### `server`

* **Description**: Contains settings for connecting to the Trust Center Agent (TCA).
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
  idMapper:
    trustCenterAgent:
      server:
        baseUrl: http://custom-tc-agent:9000
        auth: [ ... ]
        ssl: [ ... ]
  ```

### `deidentifhir` <Badge type="danger" text="Deprecated" />

::: warning Deprecated
The `deidentifhir` implementation is deprecated and will be removed in a future release. Migrate to
`idMapper` (see above). The `deidentifhirConfig` and `dateShift` fields will be removed.
:::

The old configuration format is still accepted for backward compatibility:

```yaml
deidentificator:
  deidentifhir:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
        auth: [ ... ]
        ssl: [ ... ]
    deidentifhirConfig: /path/to/TransportToRD.profile
    dateShift: P0D
```

To migrate, switch the implementation name and remove the library-specific fields:

```yaml
# Before (deprecated)
deidentificator:
  deidentifhir:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
    deidentifhirConfig: /path/to/TransportToRD.profile
    dateShift: P0D

# After
deidentificator:
  idMapper:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
```
