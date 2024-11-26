# Consent <Badge type="tip" text="Trust Center Agent" />

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
```

## Fields

### `gics.fhir`

* **Description**: Defines settings for connecting to gICS
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)

## Notes

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
