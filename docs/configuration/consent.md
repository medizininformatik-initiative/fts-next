# Consent <Badge type="tip" text="Trust Center Agent" />

This document outlines the **consent** configuration structure, its fields, and additional notes for proper usage. The configuration is critical for connecting with the GICS FHIR gateway for consent management.

## Configuration Structure

The configuration is defined in YAML format as follows:

```yaml
consent:
  gics.fhir:
    baseUrl: http://gics:8080/ttp-fhir/fhir/gics
```

## Fields

### `gics.fhir`

*  `baseUrl`
  * **Description**: Defines the base URL for the GICS FHIR gateway used for consent management.
  * **Default Value**: `http://gics:8080/ttp-fhir/fhir/gics`
  * **Notes**:
    * This URL serves as the primary endpoint for interactions with the GICS FHIR service.
    *Ensure the endpoint is accessible and conforms to the GICS FHIR gateway specifications.

## Notes

* **Customization**:
  * Update the `baseUrl` to reflect your deployment’s specific GICS FHIR gateway URL.
  * Ensure consistency with other related services.
* **Validation**:
  * Confirm that the specified URL is functional and properly authenticated.
  * Validate the configuration with YAML parsers to avoid formatting issues.
* **Integration Considerations**:
  * The GICS FHIR gateway must be operational for the consent service to function correctly.
  * Coordinate with system administrators to verify service compatibility.
* **Security Best Practices**:
  * Protect the `baseUrl` endpoint to prevent unauthorized access or data exposure.
  * Implement SSL/TLS if communicating over public or insecure networks.
