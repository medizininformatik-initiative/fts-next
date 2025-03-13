# Consent <Badge type="tip" text="Trust Center Agent" /> <Badge type="warning" text="Since 5.0" />

This document provides an overview of the `consent` section of the FTSnext
Trust Center Agent configuration file (`application.yaml`), the fields it contains, and
additional notes to clarify its usage.
The configuration is critical for connecting with the gICS FHIR gateway for consent
management.

## Configuration Example

```yaml
consent:
  gics.fhir:
    baseUrl: http://gics:8080/ttp-fhir/fhir/gics
    auth: [ ... ]
    ssl: [ ... ]
```

## Fields

### `gics.fhir` <Badge type="warning" text="Since 5.0" />

* **Description**: Defines settings for connecting to gICS
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)

### `gics.fhir.defaultPageSize` <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies the maximum number of Consents to be included in a single request.
  This parameter helps control memory usage and network load by limiting the size of data transfers.
* **Type**: Integer
* **Default**: 50
* **Example**:
  ```yaml
    pageSize: 100
  ```

## Notes

* **Domain Prerequisites**:
  * The domain specified in the CDA's project configuration **must be created in gPAS before
    FTSnext can use them**.
  * FTSnext does not create domains, so this setup must be done manually
    or through other administrative interfaces.

* **Customization**:
  * Update the `baseUrl` to reflect your deployment’s specific gICS FHIR gateway URL.
  * Ensure consistency with other related services.

* **Validation**:
  * Confirm that the specified URL is functional and properly authenticated.
  * Validate the configuration with YAML parsers to avoid formatting issues.

* **Integration Considerations**:
  * The gICS FHIR gateway must be operational for the consent service to function correctly.
  * Coordinate with system administrators to verify service compatibility.

* **Security Best Practices**:
  * Protect the `baseUrl` endpoint to prevent unauthorized access or data exposure.
  * Implement SSL/TLS if communicating over public or insecure networks.
