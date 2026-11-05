# FHIR Cohort Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.3" />

The FHIR-based implementation connects directly to a FHIR server to query for patients' consents.

## Example Configuration

```yaml
fhir:
  server:
    baseUrl: http://fhir-server:8080
    auth: [ ... ]
    ssl: [ ... ]
  patientIdentifierSystem: "http://fts.smith.care"
  # e.g.: https://simplifier.net/medizininformatikinitiative-modulconsent/2.16.840.1.113883.3.1937.777.24.5.3--20210423105554
  policySystem: "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3" # MII CS Consent Policy
  policies:
  - 2.16.840.1.113883.3.1937.777.24.5.3.2    # IDAT erheben
  - 2.16.840.1.113883.3.1937.777.24.5.3.3    # IDAT speichern, verarbeiten
  - 2.16.840.1.113883.3.1937.777.24.5.3.6    # MDAT erheben
  - 2.16.840.1.113883.3.1937.777.24.5.3.7    # MDAT speichern, verarbeiten
```

## Fields

### `server` <Badge type="warning" text="Since 5.3" />

* **Description**: Specifies connection settings for the FHIR server.
* **Type**: [`HttpClientConfig`](../../types/HttpClientConfig)
* **Example**:
  ```yaml
    server:
      baseUrl: http://fhir-server:8080
      auth: [ ... ]
      ssl: [ ... ]
  ```

### `patientIdentifierSystem` <Badge type="warning" text="Since 5.3" />

* **Description**: Filters patients based on their identifier system. Only patients with identifiers
  from this system will be included.
* **Type**: String
* **Example**:
  ```yaml
    patientIdentifierSystem: "https://example.org/fhir/identifiers/Patient"
  ```

### `policySystem` <Badge type="warning" text="Since 5.3" />

* **Description**: Filters policies based on their system identifier. Only policies from this system
  will be considered valid.
* **Type**: String
* **Example**:
  ```yaml
    policySystem: "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3" # MII CS Consent Policy
  ```

### `policies` <Badge type="warning" text="Since 5.3" />

* **Description**: Specifies a list of required policies. Consent must explicitly include approval
  for all these policies to qualify. At least one policy is required; a project without
  policies fails to load.
* **Type**: Array of Strings
* **Example**:
  ```yaml
    policies:
    - 2.16.840.1.113883.3.1937.777.24.5.3.2    # IDAT erheben
  ```

## Notes

* The `patientIdentifierSystem` and `policySystem` fields should reference valid FHIR systems.
* When defining policies, ensure they align with the consent requirements of your organization.
* The consents of a patient may be spread over several pages of the search result. A patient
  is selected as soon as the consents read so far cover all required policies. Patient
  resources with the same identifier on one page are classified together.
  * For a list of patients, the consents of a patient not yet selected are kept until the
    last page, so consents on later pages are taken into account.
  * For all patients, only the consents of patients excluded for a faulty consent are kept,
    so a later page can still select them. Keeping every patient without consent would need
    memory for each patient on the server. A patient whose required policies are spread over
    several pages is therefore not selected
    ([#2138](https://github.com/medizininformatik-initiative/fts-next/issues/2138)).
