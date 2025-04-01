# De-Identification <Badge type="tip" text="Trust Center Agent" /> <Badge type="warning" text="Since 5.0" />

This document provides an overview of the `deIdentification` section of the FTSnext
Trust Center Agent configuration file (`application.yaml`), the fields it contains, and
additional notes to clarify its usage.
This configuration is essential for managing de-identification processes and integration with
mosaic services.

::: danger Security Warning
To protect pseudonyms against brute-force attacks, it is essential to choose a sufficiently large
alphabet and salt length.
This ensures that the total number of possible combinations ($A^n$) is high enough to make reverse
computation practically infeasible.
See [Pseudonymization](../details/pseudonymisierung) for more details.
:::

## Configuration Example

```yaml
deIdentification:
  keystoreUrl: redis://keystore:6379
  gpas.fhir:
    baseUrl: http://gpas:8080/ttp-fhir/fhir/gpas
    auth: [ ... ]
    ssl: [ ... ]
  transport.ttl: PT10M
```

## Fields

### `keystoreUrl` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies the URL for the keystore service used for secure storage and retrieval
  of keys.
* **Default Value**: `redis://keystore:6379`
* **Notes**:
    * The default value assumes the use of the provided compose file configuration.
    * Ensure the Redis service at the specified URL is properly configured and running.

### `gpas.fhir` <Badge type="warning" text="Since 5.0" />

* **Description**: Defines connection settings for the gPAS FHIR gateway.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Notes**:
    * This URL is used for interacting with the FHIR service.
    * The endpoint should be accessible and compliant with the gPAS FHIR gateway specifications.

### `transport.ttl` <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies the Time-To-Live (TTL) duration for transport pseudonyms.
* **Default Value**: `PT10M` (10 minutes in ISO-8601 duration format)
* **Notes**:
    * This value determines how long transport pseudonyms remain valid.
    * Adjust this based on your security and operational requirements.

## Notes

* **Domain Prerequisites**:
    * All domains specified in the CDA's project configuration (`pseudonym`, `salt`, and
      `dateShift`) **must be created in gPAS before FTSnext can use them**.
    * FTSnext does not create domains, so this setup must be done manually
      or through other administrative interfaces.
    * To protect pseudonyms against brute-force attacks, it is essential to choose a sufficiently
      large alphabet and salt length. This ensures that the total number of possible
      combinations ($A^n$) is high enough to make reverse computation practically infeasible.

* **Customization**:
    * Each field can be customized based on deployment-specific requirements.
    * Ensure changes to the configuration are consistent with service dependencies.

* **Validation**:
    * Use YAML validation tools to verify the integrity of the configuration file.
    * Confirm that all referenced services (e.g., Redis, gPAS FHIR gateway) are available and
      properly
      authenticated.

* **Security Considerations**:
    * Secure access to the keystore and FHIR gateway endpoints to prevent unauthorized access.
    * Regularly review and update the `transport.ttl` setting to align with your data retention
      policies.
