# Data Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This document describes the configuration options available for managing data selection in the
`dataSelector` section of the project configuration file. The `dataSelector` section defines
parameters for fetching and resolving patient records from a FHIR server.

## Configuration Example

The `dataSelector` section allows different implementations to be used for selecting transfer data,
at the moment there is only one implementation available out-of-the-box: `everything`

```yaml
dataSelector:
  ignoreConsent: false
  everything:
    fhirServer:
      baseUrl: http://cd-hds:8080/fhir
      auth: [ ... ]
      ssl: [ ... ]
    resolve:
      patientIdentifierSystem: http://fts.smith.care
    pageSize: 500
```

## Fields

### `ignoreConsent` <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.3" />

* **Description**: If true this tells the implementation to ignore consent.
  The details are specific to the implementation.
* **Type**: Boolean
* **Default**: false
* **Example**:
  ```yaml
    ignoreConsent: true
  ```

### `everything` <Badge type="warning" text="Since 5.0" />

The "everything" data selector uses the FHIR servers `patient/$everything` operation to fetch data.

If ignoreConsent=false, only data within the consented period is requested from the health data
server and patient resources without a valid consented period are skipped entirely.

#### `fhirServer` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies connection settings of the FHIR server endpoint used for fetching
  patient resources.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
    fhirServer:
      baseUrl: http://my-fhir-server:8080/fhir
      auth: [ ... ]
      ssl: [ ... ]
  ```

#### `resolve.patientIdentifierSystem` <Badge type="danger" text="Deprecated" /> <Badge type="warning" text="Since 5.0" />

* **Description**: Defines the system URL used to resolve patient identifiers within the FHIR
  server. This option will be removed with the next release, use the `patientIdentifierSystem` in 
  the CohortSelector instead.
* **Type**: String
* **Example**:
  ```yaml
    resolve:
      patientIdentifierSystem: http://custom.identifier.system
  ```

#### `pageSize` <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.1" />

* **Description**: Specifies the maximum number of FHIR resources to be included in a single bundle
  when requesting data from the clinical domain health data storage (cd-hds).
  This parameter helps control memory usage and network load by limiting the size of data transfers.
* **Type**: Integer
* **Default**: 500
* **Example**:
  ```yaml
    pageSize: 1000

## Notes

* The `patientIdentifierSystem` field must be a valid system URL used for resolving patient
  identifiers in the FHIR server.
* Ensure the FHIR server endpoint is accessible and configured correctly for data retrieval.

## References

* [Operation $everything on Patient](https://www.hl7.org/fhir/R4/patient-operation-everything.html)
* [Blaze Documentation](https://samply.github.io/blaze/api/operation-patient-everything)
