# Cohort Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This document describes the configuration options available for managing cohort selection settings
in the `cohortSelector` section of the project configuration file. These settings control how
consent data is retrieved and filtered.

## Example Configuration

The `cohortSelector` section allows different implementations to be used for selecting the transfer
cohort. The following implementations are available out-of-the-box:

```yaml
cohortSelector:
  trustCenterAgent:
    server:
      baseUrl: http://tc-agent:8080
      auth: [ ... ]
      ssl: [ ... ]
    domain: MII
    patientIdentifierSystem: "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym"
    # e.g.: https://simplifier.net/medizininformatikinitiative-modulconsent/2.16.840.1.113883.3.1937.777.24.5.3--20210423105554
    policySystem: "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3" # MII CS Consent Policy
    policies:
    - 2.16.840.1.113883.3.1937.777.24.5.3.2    # IDAT erheben
    - 2.16.840.1.113883.3.1937.777.24.5.3.3    # IDAT speichern, verarbeiten
    - 2.16.840.1.113883.3.1937.777.24.5.3.6    # MDAT erheben
    - 2.16.840.1.113883.3.1937.777.24.5.3.7    # MDAT speichern, verarbeiten
```

## Fields

### `trustCenterAgent`
Configuration for the [Trust Center Agent based cohort selector](./cohort-selector/trustCenterAgent).

### `fhir`
Configuration for the [FHIR server based cohort selector](./cohort-selector/fhir).

### `external`
Configuration for the [External cohort selector](./cohort-selector/external).
