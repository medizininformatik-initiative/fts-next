# Cohort Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This document describes the configuration options available for managing cohort selection settings
in the `cohortSelector` section of the project configuration file. These settings control how
consent data is retrieved and filtered.

## Configuration Structure

The `cohortSelector` section allows different implementations to be used for selecting the transfer
cohort, at the moment there is only one implementation available out-of-the-box: `trustCenterAgent`.

```yaml
cohortSelector:
  trustCenterAgent:
    server:
      baseUrl: http://tc-agent:8080
    domain: MII
    patientIdentifierSystem: "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym"
    policySystem: "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy"
    policies: [ "IDAT_erheben", "IDAT_speichern_verarbeiten", "MDAT_erheben", "MDAT_speichern_verarbeiten" ]
```

## Fields

### `trustCenterAgent` <Badge type="warning" text="Since 5.0" />

The TCA based implementation uses the connection to the trust center to select patient IDs for
transfer.

#### `server` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies connection settings for the Trust Center Agent server.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
    server:
      baseUrl: http://custom-agent:9000
      auth: [ ... ]
      ssl: [ ... ]
  ```

#### `domain` <Badge type="warning" text="Since 5.0" />

* **Description**: Defines the domain to search for consent, serving as a namespace for data
  segregation.
* **Type**: String
* **Example**:
  ```yaml
    domain: ResearchDomain
  ```
* **Important**: This domain must already exist in gICS before FTSnext can use it. FTSnext cannot
  create or alter domains through the FHIR gateway.

#### `patientIdentifierSystem` <Badge type="warning" text="Since 5.0" />

* **Description**: Filters patients based on their identifier system. Only patients with identifiers
  from this system will be included.
* **Type**: String
* **Example**:
  ```yaml
    patientIdentifierSystem: "https://example.org/fhir/identifiers/Patient"
  ```

#### `policySystem` <Badge type="warning" text="Since 5.0" />

* **Description**: Filters policies based on their system identifier. Only policies from this system
  will be considered valid.
* **Type**: String
* **Example**:
  ```yaml
    policySystem: "https://example.org/fhir/CodeSystem/Policy"
  ```

#### `policies` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies a list of required policies. Consent must explicitly include approval
  for all these policies to qualify.
* **Type**: Array of Strings
* **Example**:
  ```yaml
    policies: [ "IDAT_view", "MDAT_process" ]
  ```

## Notes
* The `domain` field should be chosen carefully to avoid overlapping data between namespaces.
* Ensure that `domain` is correctly configured in the trust center.
* The `patientIdentifierSystem` and `policySystem` fields must reference valid FHIR-based system
  URLs.
* When defining policies, ensure they align with the consent requirements of your organization.
