# Bundle Sender <Badge type="tip" text="Research Domain Agent" />

This document describes the configuration options available for managing the `bundleSender` section
of the configuration file. This section is responsible for defining how bundles are sent after being
processed by the RDA.

## Configuration Structure

The `bundleSender` section allows different implementations to be used for sending processed
bundles, at the moment there is only one implementation available out-of-the-box:
`fhirStore`.

```yaml
bundleSender:
  fhirStore:
    server:
      baseUrl: http://rd-hds:8080/fhir
      auth: [ ... ]
      ssl: [ ... ]
```

## Fields

### `fhirStore`

This implementation sends processed bundles to a FHIR store.

#### `server`

* **Description**: Contains settings for connecting to the RDA server.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
