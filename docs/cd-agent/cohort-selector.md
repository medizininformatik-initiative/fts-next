# Cohort Selector <Badge type="tip" text="Clinical Domain Agent" />

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

### `trustCenterAgent`

The TCA based implementation uses the connection to the trust center to select patient IDs for
transfer.

#### `server`

* **Description**: Specifies connection settings for the Trust Center Agent server.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
    server:
      baseUrl: http://custom-agent:9000
  ```

#### `domain`

* **Description**: Defines the domain to search for consent, serving as a namespace for data
  segregation.
* **Type**: String
* **Example**:
  ```yaml
    domain: ResearchDomain
  ```

#### `patientIdentifierSystem`

* **Description**: Filters patients based on their identifier system. Only patients with identifiers
  from this system will be included.
* **Type**: String
* **Example**:
  ```yaml
    patientIdentifierSystem: "https://example.org/fhir/identifiers/Patient"
  ```

#### `policySystem`

* **Description**: Filters policies based on their system identifier. Only policies from this system
  will be considered valid.
* **Type**: String
* **Example**:
  ```yaml
    policySystem: "https://example.org/fhir/CodeSystem/Policy"
  ```

#### `policies`

* **Description**: Specifies a list of required policies. Consent must explicitly include approval
  for all these policies to qualify.
* **Type**: Array of Strings
* **Example**:
  ```yaml
    policies: [ "IDAT_view", "MDAT_process" ]
  ```

## Notes

* The `domain` field should be chosen carefully to avoid overlapping data between namespaces.
* The `patientIdentifierSystem` and `policySystem` fields must reference valid FHIR-based system
  URLs.
* When defining policies, ensure they align with the consent requirements of your organization.