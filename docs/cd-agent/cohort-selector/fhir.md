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
* The cohort selector pages over patients and searches the consents of each page of patients
  separately, so each patient is selected based on all of its consents:
  1. Patients, for all patients: `Patient?_has:Consent:patient:status=active`;
     for a list of patients: `Patient?identifier=<patientIdentifierSystem>|<id>,…`
  2. For each page of patients, the consents of any status of all patient resources with the
     identifiers of these patients, together with these patient resources, following the
     result's `next` links:
     `Consent?patient.identifier=<patientIdentifierSystem>|<id>,…&_include=Consent:patient`
* The FHIR server must support the `_has` search parameter, the chained `patient.identifier`
  search parameter of `Consent` with several comma-separated values, and
  `_include=Consent:patient`, e.g. [Blaze](https://github.com/samply/blaze).
* Both searches use GET and set no `_count`, so pages have the server's default size, e.g.
  50 on Blaze, 20 on HAPI FHIR, 10 on Azure Health Data Services, which keeps the consent
  search URL short. A large default page size on the server makes the consent search URL
  long, as it lists the identifier of every patient of a page, and may exceed the request
  line limit of the server or of proxies in between.
* Consents are not searched with `_revinclude`, as several servers cut the resources it adds
  to a page, e.g. HAPI FHIR at 1000 per page by default without notice and Google Cloud
  Healthcare API at 100 per parameter. Azure Health Data Services marks cut results and links
  to the rest, which a client has to follow separately. `_include=Consent:patient` adds at
  most one patient per consent, so it stays below these limits at the default page sizes.
* Consents must reference their patient by a literal reference, e.g. `Patient/123`. Consents
  referencing their patient by identifier only are not found.
* Patient resources with the same identifier are classified together with all their
  consents, whichever page they are on, and only once: a duplicate patient resource on a
  later page is ignored and counted in the warning `Ignoring N duplicate patients already
  read on an earlier page`.
* A requested patient whose consents are all inactive or rejected is reported as `No consent
  covering all required policies`; one without any consent as `No consent found`.
