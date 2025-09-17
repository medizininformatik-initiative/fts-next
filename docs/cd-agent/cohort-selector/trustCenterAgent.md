# TCA Cohort Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.0" />

The TCA based implementation uses the connection to the TCA to select patient IDs for transfer.

## Example Configuration

```yaml
trustCenterAgent:
  server:
    baseUrl: http://tc-agent:8080
    auth: [ ... ]
    ssl: [ ... ]
  domain: MII
  patientIdentifierSystem: "http://fts.smith.care"
  # e.g.: https://simplifier.net/medizininformatikinitiative-modulconsent/2.16.840.1.113883.3.1937.777.24.5.3--20210423105554
  policySystem: "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3" # MII CS Consent Policy
  policies:
  - 2.16.840.1.113883.3.1937.777.24.5.3.2    # IDAT erheben
  - 2.16.840.1.113883.3.1937.777.24.5.3.3    # IDAT speichern, verarbeiten
  - 2.16.840.1.113883.3.1937.777.24.5.3.6    # MDAT erheben
  - 2.16.840.1.113883.3.1937.777.24.5.3.7    # MDAT speichern, verarbeiten
  signerIdType: "Pseudonym"
```

## Fields

### `server` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies connection settings for the Trust Center Agent server.
* **Type**: [`HttpClientConfig`](../../types/HttpClientConfig)
* **Example**:
  ```yaml
    server:
      baseUrl: http://custom-agent:9000
      auth: [ ... ]
      ssl: [ ... ]
  ```

### `domain` <Badge type="warning" text="Since 5.0" />

* **Description**: Defines the domain to search for consent, serving as a namespace for data
  segregation.
* **Type**: String
* **Example**:
  ```yaml
    domain: ResearchDomain
  ```
* **Important**: This domain must already exist in gICS before FTSnext can use it. FTSnext cannot
  create or alter domains through the FHIR gateway.

### `patientIdentifierSystem` <Badge type="warning" text="Since 5.0" />

* **Description**: Adds the identifier system to the patients, regardless of the system in gICS.
* **Type**: String
* **Example**:
  ```yaml
    patientIdentifierSystem: "https://example.org/fhir/identifiers/Patient"
  ```

### `policySystem` <Badge type="warning" text="Since 5.0" />

* **Description**: Filters policies based on their system identifier. Only policies from this system
  will be considered valid.
* **Type**: String
* **Example**:
  ```yaml
    policySystem: "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3" # MII CS Consent Policy
  ```

### `policies` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies a list of required policies. Consent must explicitly include approval
  for all these policies to qualify.
* **Type**: Array of Strings
* **Example**:
  ```yaml
    policies:
    - 2.16.840.1.113883.3.1937.777.24.5.3.2    # IDAT erheben
  ```

### `signerIdType` <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.4.1" />

* **Description**: Specifies the type of identifier used for consent signing in the Trust Center Agent.
  This field is used to construct the GICS identifier system URL for patient identification.
* **Type**: String
* **Default**: "Pseudonym"
* **Example**:
  ```yaml
    signerIdType: "Pseudonym"
  ```

## Notes

* The `domain` field should be chosen carefully to avoid overlapping data between namespaces.
* Ensure that `domain` is correctly configured in the trust center.
* The `patientIdentifierSystem` and `policySystem` fields should reference valid FHIR systems.
* When defining policies, ensure they align with the consent requirements of your organization.
